package com.amyrmo.damavand.core.adapters

import android.content.Context
import com.amyrmo.damavand.core.CoreAdapter
import com.amyrmo.damavand.core.HopContext
import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.core.HopHandle
import com.amyrmo.damavand.core.PreparedHop
import com.amyrmo.damavand.core.StageTracker
import com.amyrmo.damavand.core.StopReason
import com.amyrmo.damavand.core.supervisor.ProcessSupervisor
import com.amyrmo.damavand.logic.AetherCommands
import com.amyrmo.damavand.logic.HopConfig
import com.amyrmo.damavand.logic.PlannedHop
import com.amyrmo.damavand.logic.SocksEndpoint
import com.amyrmo.damavand.model.AetherKeyKind
import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.components.ConnectionStage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Aether hop adapter (architecture §2.5): a thin contract shell over
 * [AetherEngine]. The plan is trusted — `HopConfig.AetherRuntime.argv`
 * already begins with `--bind` for generated mode and embeds
 * [AetherCommands.customArguments] output for custom mode — so prepare()
 * resolves only what the plan could not:
 *  - the start-time upstream splice ([HopConfig.AetherRuntime.upstreamPending]):
 *    the previous hop's now-known listener is appended as `--upstream`,
 *    unless a custom command already names one of its own;
 *  - the bind port adoption: a custom command's own listener port wins
 *    over the plan's allocated port.
 * start() then hands the resolved argv to the engine via
 * [AetherEngine.preparedArgv] and reports the port the engine actually
 * adopted ([AetherEngine.socksPort]) as the hop's listener (§2.4 rule 2).
 */
class AetherAdapter(private val supervisor: ProcessSupervisor) : CoreAdapter {

    override val kind: CoreKind = CoreKind.AETHER

    /**
     * The application context of the most recent hop this adapter prepared.
     * The keys-management surface below is called from the UI, outside any
     * session, so its no-context overloads fall back to this; the
     * context-taking overloads are the primary form.
     */
    @Volatile private var lastAppContext: Context? = null

    private class AetherPrepared(
        override val hop: PlannedHop,
        override val ctx: HopContext,
        val profile: AetherProfile,
        val argv: List<String>,
        val bindPort: Int,
    ) : PreparedHop

    override fun prepare(ctx: HopContext, hop: PlannedHop, upstream: SocksEndpoint?): PreparedHop {
        lastAppContext = ctx.appContext
        val config = hop.config as? HopConfig.AetherRuntime
            ?: throw IllegalStateException(
                "aether hop ${hop.index} carries ${hop.config::class.simpleName}, expected AetherRuntime"
            )
        val profile = config.profile
        var argv = config.argv
        if (config.upstreamPending && upstream != null) {
            // A custom command that names its own --upstream keeps it (the
            // planner applies the same rule for plan-time carriers); a
            // generated argv never carries one while the splice is pending,
            // because the runtime profile's upstream was cleared.
            val customNamesOwn = AetherCommands.isCustom(profile) && "--upstream" in argv
            if (!customNamesOwn) {
                argv = argv + listOf("--upstream", "socks5://127.0.0.1:${upstream.port}")
            }
        }
        val bindPort = if (AetherCommands.isCustom(profile)) {
            AetherCommands.listenerPortOf(argv) ?: config.bindPort
        } else {
            config.bindPort
        }
        return AetherPrepared(hop, ctx, profile, argv, bindPort)
    }

    override suspend fun start(prepared: PreparedHop): HopHandle {
        val p = prepared as? AetherPrepared
            ?: throw IllegalStateException("AetherAdapter.start got a hop it did not prepare")
        val ctx = p.ctx
        val hop = p.hop
        return withContext(Dispatchers.IO) {
            val tracker = StageTracker(ctx, ConnectionStage.Starting)
            val stopped = CompletableDeferred<StopReason>()
            val stopRequested = AtomicBoolean(false)
            val deathReason = AtomicReference<String?>(null)

            val engine = AetherEngine(
                context = ctx.appContext,
                supervisor = supervisor,
                config = p.profile,
                initialPort = p.bindPort,
                onLog = { line -> ctx.log(line) },
                onStopped = { reason ->
                    if (stopRequested.get()) {
                        stopped.complete(StopReason.Requested)
                    } else {
                        deathReason.set(reason)
                        stopped.complete(StopReason.Died(reason ?: "aether exited"))
                    }
                },
            )
            engine.preparedArgv = p.argv

            tracker.set(ConnectionStage.Starting)
            engine.start()
            if (!engine.isRunning) {
                // start() reports an immediate failure (e.g. a missing
                // binary) through onStopped before returning.
                throw HopFailure(
                    hop.index, kind, hop.profile.name, ConnectionStage.Starting,
                    deathReason.get() ?: "aether core failed to start",
                )
            }
            tracker.set(ConnectionStage.Authenticating)

            // Readiness in 30 s slices up to the core's connect deadline
            // (the v1 service pattern): awaitReady returns early on death,
            // and a death between slices ends the wait as a failure.
            val deadlineMs = ProcessSupervisor.deadlineFor(CoreKind.AETHER)
            var waitedMs = 0L
            var ready = false
            while (waitedMs < deadlineMs) {
                val sliceMs = minOf(READY_SLICE_MS, deadlineMs - waitedMs)
                if (engine.awaitReady(sliceMs)) {
                    ready = true
                    break
                }
                waitedMs += sliceMs
                if (!engine.isRunning) break
            }
            if (!ready) {
                val died = deathReason.get()
                stopRequested.set(true)
                runCatching { engine.stop() }
                throw HopFailure(
                    hop.index, kind, hop.profile.name, ConnectionStage.Authenticating,
                    died ?: "aether core did not become ready",
                )
            }
            tracker.set(ConnectionStage.Verifying)

            HopHandle(
                // The adopted port, read off the started engine — never
                // re-derived from the config (§2.4 rule 2).
                socks = SocksEndpoint(port = engine.socksPort),
                stages = tracker.flow,
                traffic = { null },
                stopped = stopped,
                stop = {
                    stopRequested.set(true)
                    withContext(Dispatchers.IO) { runCatching { engine.stop() } }
                    stopped.complete(StopReason.Requested)
                },
            )
        }
    }

    // -- keys management (§2.5) ------------------------------------------------
    //
    // Adapter-level helpers over throwaway (never started) engines; not
    // part of the CoreAdapter contract. The engine works on the profile's
    // protocol kind and the shared identity folder, so the profile value
    // itself is never mutated.

    /** The keys state of [profile]'s protocol kind, read with an explicit context. */
    fun keysStatus(context: Context, profile: AetherProfile): AetherKeysStatus {
        val engine = throwawayEngine(context, profile)
        return AetherKeysStatus(kind = engine.registrationKind(), missingFiles = engine.missingKeyFiles())
    }

    fun keysStatus(profile: AetherProfile): AetherKeysStatus =
        keysStatus(requireAppContext(), profile)

    /**
     * Registers fresh keys of [profile]'s kind; true when the new keys were
     * confirmed complete and settled over the old ones. Bridges the
     * engine's callback flow over a [CompletableDeferred]; cancellation
     * abandons the wait, not the registration thread (the engine's own
     * watchdog bounds it, and an interrupted settle is finished by the
     * next engine that reads the identity folder).
     */
    suspend fun renewKeys(context: Context, profile: AetherProfile): Boolean {
        val engine = throwawayEngine(context, profile)
        val done = CompletableDeferred<Boolean>()
        engine.registerKeys(engine.registrationKind().register) { ok, _ -> done.complete(ok) }
        return done.await()
    }

    suspend fun renewKeys(profile: AetherProfile): Boolean =
        renewKeys(requireAppContext(), profile)

    /**
     * Deletes the identity files in use (the .toml files in
     * `filesDir/<WORK_DIR>`), so
     * the next start re-registers from nothing. The renewal staging folder
     * is left alone — [AetherEngine]'s settle owns it.
     */
    fun resetKeys(context: Context, profile: AetherProfile) {
        val dir = File(context.filesDir, AetherEngine.WORK_DIR)
        dir.listFiles { file -> file.isFile && file.name.endsWith(".toml") }
            ?.forEach { it.delete() }
    }

    fun resetKeys(profile: AetherProfile) = resetKeys(requireAppContext(), profile)

    /** The identity files that exist, file name → contents, for the UI's export flow. */
    fun exportKeys(context: Context, profile: AetherProfile): Map<String, String> {
        val dir = File(context.filesDir, AetherEngine.WORK_DIR)
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".toml") }
            ?: return emptyMap()
        return files.sortedBy { it.name }.associate { it.name to it.readText() }
    }

    fun exportKeys(profile: AetherProfile): Map<String, String> =
        exportKeys(requireAppContext(), profile)

    private fun throwawayEngine(context: Context, profile: AetherProfile): AetherEngine =
        AetherEngine(
            context = context,
            supervisor = supervisor,
            config = profile,
            initialPort = 0,
            onLog = {},
            onStopped = {},
        )

    private fun requireAppContext(): Context = lastAppContext
        ?: throw IllegalStateException(
            "AetherAdapter has no application context yet; call the Context-taking overload (or prepare a hop first)"
        )

    private companion object {
        /** Readiness slice for the connect-deadline wait (the v1 service pattern). */
        const val READY_SLICE_MS = 30_000L
    }
}

/** The WARP-keys state of one Aether protocol kind (architecture §2.5). */
data class AetherKeysStatus(
    val kind: AetherKeyKind,
    val missingFiles: List<String>,
) {
    val complete: Boolean get() = missingFiles.isEmpty()
}
