package com.amyrmo.damavand.core.session

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.amyrmo.damavand.core.CoreAdapter
import com.amyrmo.damavand.core.CoreEvent
import com.amyrmo.damavand.core.HopContext
import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.core.HopHandle
import com.amyrmo.damavand.core.LogBus
import com.amyrmo.damavand.core.adapters.SingBoxAdapter
import com.amyrmo.damavand.core.bridge.PreparedBridge
import com.amyrmo.damavand.core.bridge.TunForwarder
import com.amyrmo.damavand.logic.BridgeConfigBuilder
import com.amyrmo.damavand.logic.HopConfig
import com.amyrmo.damavand.logic.PerAppSelection
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.SessionPlan
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.components.ConnectionStage
import com.amyrmo.damavand.ui.components.StageInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the runner needs from the hosting [VpnService] to build a TUN for
 *  the standalone-Xray forwarder path (§2.2): the service's Builder is the
 *  only TUN maker there; every other path's TUN belongs to libbox. */
data class TunRequest(
    val sessionName: String,
    val mtu: Int,
    val perApp: PerAppSelection,
)

/** The Android-side environment a session runs in (owned by the service). */
class SessionEnvironment(
    val appContext: Context,
    val vpnService: VpnService,
    val filesDir: File,
    val scope: CoroutineScope,
    /** Establish the TUN for the standalone-Xray path; null on failure. */
    val establishTun: (TunRequest) -> ParcelFileDescriptor?,
)

/** A fully started session: everything the orchestrator must watch/stop. */
class SessionRun internal constructor(
    /** Distinct hop handles in start order (run members share their start's handle). */
    val hopHandles: List<HopHandle>,
    /** The handle serving each hop index (collapsed run members share their run start's). */
    val hopHandlesByIndex: List<HopHandle>,
    val bridgeHandle: HopHandle?,
    /** The advertised local proxy port — from the started artefact (§2.4 rule 2). */
    val localSocksPort: Int,
    /**
     * Completes with a reason if the TUN forwarder fails AFTER the
     * session connected (TunForwarder's 6 s zeptun→hev proof switch can
     * fail late); never completes while the forwarder is healthy.
     */
    val tunFailure: kotlinx.coroutines.Deferred<String> =
        kotlinx.coroutines.CompletableDeferred(),
    private val forwarderActive: Boolean,
) {
    /** All distinct handles, bridge first (it is stopped first). */
    val allHandles: List<HopHandle> =
        (listOfNotNull(bridgeHandle) + hopHandles).distinct()

    private var stopped = false

    /** Reverse-order teardown (§4.3 step 5): forwarder, bridge, hops reversed. */
    suspend fun stop() {
        if (stopped) return
        stopped = true
        if (forwarderActive) runCatching { TunForwarder.stop() }
        bridgeHandle?.let { runCatching { it.stop() } }
        hopHandles.asReversed().forEach { handle ->
            runCatching { handle.stop() }
        }
    }
}

/**
 * Executes a [SessionPlan] (architecture §4.3): hops start in traffic
 * order, each awaiting its own readiness before the next begins; pending
 * splices are resolved with the previous hop's reported listener; the
 * bridge (or the standalone TUN owner) starts last; the end-to-end probe
 * gates Connected. The runner executes the plan — it never improvises one.
 */
class SessionRunner(
    private val env: SessionEnvironment,
    private val adapters: Map<CoreKind, CoreAdapter>,
    private val singBoxAdapter: SingBoxAdapter,
) {

    suspend fun run(
        plan: SessionPlan,
        device: BridgeConfigBuilder.DeviceOptions,
        onStage: (StageInfo) -> Unit,
    ): SessionRun {
        val n = plan.hops.size
        val handles = arrayOfNulls<HopHandle>(n)
        var bridgeHandle: HopHandle? = null
        var forwarderActive = false
        val tunFailure = kotlinx.coroutines.CompletableDeferred<String>()
        try {
            // -- hops, entry → exit --------------------------------------
            for (i in 0 until n) {
                val hop = plan.hops[i]
                if (hop.config is HopConfig.XrayRunMember) {
                    // Collapsed into the run start's single Xray instance.
                    handles[i] = handles[(hop.config as HopConfig.XrayRunMember).runStartIndex]
                    continue
                }
                val adapter = adapters[hop.kind]
                    ?: throw HopFailure(i, hop.kind, hop.profile.name, ConnectionStage.Starting, "no adapter for ${hop.kind}")
                var lastStage = ConnectionStage.Starting
                val ctx = hopContext(plan, hop, i, n) { stageInfo ->
                    lastStage = stageInfo.stage
                    onStage(stageInfo)
                }
                val upstream = if (needsSplice(hop.config)) {
                    handles[i - 1]?.socks
                        ?: throw HopFailure(i, hop.kind, hop.profile.name, ConnectionStage.Starting, "previous hop has no listener to splice into")
                } else {
                    null
                }
                val prepared = try {
                    adapter.prepare(ctx, hop, upstream)
                } catch (failure: HopFailure) {
                    throw failure
                } catch (e: Exception) {
                    throw HopFailure(i, hop.kind, hop.profile.name, lastStage, e.message ?: "prepare failed", e)
                }
                onStage(StageInfo(ConnectionStage.Starting, hopLabel(plan, hop)))
                handles[i] = try {
                    adapter.start(prepared)
                } catch (failure: HopFailure) {
                    throw failure
                } catch (e: Exception) {
                    throw HopFailure(i, hop.kind, hop.profile.name, lastStage, e.message ?: "start failed", e)
                }
            }

            val exitHop = plan.hops.last()
            val exitHandle = handles[n - 1]
                ?: throw HopFailure(n - 1, exitHop.kind, exitHop.profile.name, ConnectionStage.Starting, "exit hop produced no handle")

            // -- TUN owner ------------------------------------------------
            val localPort: Int
            val bridgePlan = plan.bridge
            when {
                bridgePlan != null -> {
                    val json = bridgePlan.json ?: BridgeConfigBuilder.bridge(
                        exitSocksPort = exitHandle.socks.port,
                        localPort = bridgePlan.localPort,
                        device = bridgePlan.device,
                        dns = bridgePlan.dns,
                        tun = !plan.proxyOnly,
                    ).json
                    // The artefact carries its own effective port (§2.4 rule 2).
                    val artefact = PreparedBridge(json, bridgePlan.localPort)
                    val bctx = bridgeContext(plan) { onStage(it) }
                    val prepared = singBoxAdapter.prepareBridge(bctx, artefact)
                    onStage(StageInfo(ConnectionStage.Verifying, hopLabel = "Tunnel"))
                    bridgeHandle = try {
                        singBoxAdapter.start(prepared)
                    } catch (failure: HopFailure) {
                        throw failure
                    } catch (e: Exception) {
                        throw HopFailure(n - 1, exitHop.kind, exitHop.profile.name, ConnectionStage.Verifying, e.message ?: "bridge failed to start", e)
                    }
                    localPort = bridgeHandle.socks.port
                }
                exitHop.kind == CoreKind.XRAY -> {
                    // Standalone Xray owns the TUN through the forwarder (§2.2).
                    if (!plan.proxyOnly) {
                        val pfd = env.establishTun(
                            TunRequest(
                                sessionName = exitHop.profile.name,
                                mtu = device.mtu,
                                perApp = plan.perApp,
                            ),
                        ) ?: throw HopFailure(n - 1, exitHop.kind, exitHop.profile.name, ConnectionStage.Starting, "the VPN interface could not be established")
                        val started = TunForwarder.start(
                            service = env.vpnService,
                            tun = pfd,
                            socksPort = exitHandle.socks.port,
                            mtu = device.mtu,
                            ipv4 = FORWARDER_IPV4,
                            ipv6 = null,
                            scope = env.scope,
                            onFailure = { reason ->
                                LogBus.append("E/session: tun forwarder failed after start: $reason")
                                tunFailure.complete(reason)
                            },
                        )
                        if (!started) {
                            runCatching { pfd.close() }
                            throw HopFailure(n - 1, exitHop.kind, exitHop.profile.name, ConnectionStage.Starting, "the TUN forwarder failed to start")
                        }
                        forwarderActive = true
                    }
                    localPort = exitHandle.socks.port
                }
                else -> {
                    // Standalone sing-box full config: libbox established
                    // the TUN itself through the platform interface.
                    localPort = exitHandle.socks.port
                }
            }

            // -- end-to-end probe (§4.3 step 4) ---------------------------
            onStage(StageInfo(ConnectionStage.Verifying, hopLabel = "Verifying", subStatus = "checking the connection end to end"))
            val verified = withContext(Dispatchers.IO) { EndToEndProbe.verify(localPort) }
            if (!verified) {
                throw HopFailure(
                    n - 1, exitHop.kind, exitHop.profile.name, ConnectionStage.Verifying,
                    "the end-to-end probe got no answer through the session proxy",
                )
            }
            onStage(StageInfo(ConnectionStage.Connected))

            return SessionRun(
                hopHandles = handles.filterNotNull().distinct(),
                hopHandlesByIndex = handles.map { it!! },
                bridgeHandle = bridgeHandle,
                localSocksPort = localPort,
                tunFailure = tunFailure,
                forwarderActive = forwarderActive,
            )
        } catch (failure: HopFailure) {
            teardown(handles.filterNotNull().distinct(), bridgeHandle, forwarderActive)
            throw failure
        } catch (e: Exception) {
            teardown(handles.filterNotNull().distinct(), bridgeHandle, forwarderActive)
            throw HopFailure(
                0, plan.hops.firstOrNull()?.kind ?: CoreKind.XRAY,
                plan.hops.firstOrNull()?.profile?.name.orEmpty(),
                ConnectionStage.Starting, e.message ?: "session failed", e,
            )
        }
    }

    private suspend fun teardown(hops: List<HopHandle>, bridge: HopHandle?, forwarderActive: Boolean) {
        if (forwarderActive) runCatching { TunForwarder.stop() }
        bridge?.let { runCatching { it.stop() } }
        hops.asReversed().forEach { runCatching { it.stop() } }
    }

    private fun needsSplice(config: HopConfig): Boolean = when (config) {
        is HopConfig.XrayJson -> config.carrierSplicePending
        is HopConfig.XrayRunStart -> config.carrierSplicePending
        is HopConfig.PsiphonJson -> config.carrierSplicePending
        is HopConfig.AetherRuntime -> config.upstreamPending
        is HopConfig.MihomoYaml -> config.splicePending
        else -> false
    }

    private fun hopContext(
        plan: SessionPlan,
        hop: PlannedHop,
        index: Int,
        hopCount: Int,
        onStage: (StageInfo) -> Unit,
    ): HopContext {
        val dir = File(env.filesDir, hop.sessionDirName).apply { mkdirs() }
        return HopContext(
            appContext = env.appContext,
            vpnService = env.vpnService,
            sessionDir = dir,
            hopIndex = index,
            emit = { event ->
                when (event) {
                    is CoreEvent.LogLine -> LogBus.append(event.text)
                    is CoreEvent.StageChanged -> onStage(
                        StageInfo(event.stage, hopLabel = hopLabel(plan, hop), subStatus = event.subStatus),
                    )
                }
            },
        )
    }

    private fun bridgeContext(plan: SessionPlan, onStage: (StageInfo) -> Unit): HopContext {
        val dir = File(env.filesDir, "sessions/${plan.sessionId}/bridge").apply { mkdirs() }
        return HopContext(
            appContext = env.appContext,
            vpnService = env.vpnService,
            sessionDir = dir,
            // Bridge failures are attributed to the exit hop (§4.3 step 5).
            hopIndex = plan.hops.lastIndex,
            emit = { event ->
                when (event) {
                    is CoreEvent.LogLine -> LogBus.append(event.text)
                    is CoreEvent.StageChanged -> onStage(
                        StageInfo(event.stage, hopLabel = "Tunnel", subStatus = event.subStatus),
                    )
                }
            },
        )
    }

    private fun hopLabel(plan: SessionPlan, hop: PlannedHop): String {
        val kindLabel = hop.kind.name.lowercase().replaceFirstChar { it.uppercase() }
        return if (plan.hops.size > 1) "$kindLabel — hop ${hop.index + 1} of ${plan.hops.size}" else kindLabel
    }

    companion object {
        /** The forwarder-side address of v1's Option2 interface (client .2; the service owns the .1 router address). */
        const val FORWARDER_IPV4 = "10.1.0.2"
    }
}
