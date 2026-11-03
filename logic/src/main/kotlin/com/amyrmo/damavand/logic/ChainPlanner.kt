package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AutoSelectProfile
import com.amyrmo.damavand.model.ChainProfile
import com.amyrmo.damavand.model.ConfigParser
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.PsiphonProfile
import com.amyrmo.damavand.model.SingBoxProfile
import com.amyrmo.damavand.model.WhitednsProfile
import com.amyrmo.damavand.model.XrayPayload
import com.amyrmo.damavand.model.XrayProfile

/** Resolves chain member ids to profiles (the repository, in :app terms). */
fun interface ProfileLookup {
    fun profile(id: String): Profile?
}

/** App settings the planner needs, distilled (§4.5 step 1). */
data class PlanSettings(
    /** Fallback session DNS when the exit profile carries none (§4.6). */
    val sessionDns: String = "",
    val routing: RoutingMode = RoutingMode.GLOBAL,
    val perApp: PerAppSelection = PerAppSelection.All,
    val proxyOnly: Boolean = false,
    val xrayOptions: XrayConfigBuilder.BuildOptions = XrayConfigBuilder.BuildOptions(),
    val device: BridgeConfigBuilder.DeviceOptions = BridgeConfigBuilder.DeviceOptions(),
)

/**
 * The chain planner (architecture §4.5): target profile → [SessionPlan].
 *
 * Steps: resolve members (V1) → validate (V2–V9) → collapse Xray runs →
 * allocate ports entry→exit from [PortAllocator] → generate each hop's
 * config with its upstream wired (§4.4) → decide the TUN owner and build
 * the bridge (§4.5 step 6) → DNS by §4.6 precedence.
 *
 * Contracts the session layer (§2) relies on:
 *  - hop order is entry→exit; [PlannedHop.upstream] of hop *i* is hop
 *    *i−1*'s listener (Fixed when known at plan time, FromHop when the
 *    previous hop is Psiphon and its port is runtime-dynamic);
 *  - a config carrying a pending flag must be spliced at start time with
 *    the named :logic function once the previous hop reports its port;
 *  - [BridgePlan.json] is null exactly for a Psiphon exit — build it then
 *    via [BridgeConfigBuilder.bridge] with the resolved exit port;
 *  - member directories: chains → `chains/<sessionId>/hop-<i>`,
 *    standalone → `sessions/<sessionId>`.
 */
object ChainPlanner {

    /** The bridge's local listener port (§2.4). */
    const val BRIDGE_LOCAL_PORT = 10808

    fun plan(
        target: Profile,
        sessionId: String,
        settings: PlanSettings = PlanSettings(),
        lookup: ProfileLookup,
        allocator: PortAllocator = PortAllocator(),
    ): SessionPlan {
        // -- resolve (V1) ---------------------------------------------------
        val members: List<Profile> = when (target) {
            is ChainProfile -> {
                val missing = mutableListOf<ChainError>()
                val resolved = target.hopIds.mapIndexed { i, id ->
                    val p = lookup.profile(id)
                    if (p == null) missing += ChainError.MissingMember(id, i)
                    p
                }
                if (missing.isNotEmpty()) throw ChainException(missing)
                resolved.filterNotNull()
            }
            is AutoSelectProfile -> throw ChainException(
                listOf(ChainError.UnplannableTarget("AutoSelect targets are expanded by the app layer before planning (§4.2)")),
            )
            else -> listOf(target)
        }

        // -- validate ---------------------------------------------------------
        val isChain = members.size > 1
        val warnings = mutableListOf<ChainWarning>()
        if (isChain) {
            val result = ChainValidator.validateProfiles(members)
            if (!result.isValid) throw ChainException(result.errors)
            warnings += result.warnings
        } else {
            // A standalone WhiteDNS profile gets the same member checks (V7).
            (members.single() as? WhitednsProfile)?.let { w ->
                val problems = ChainValidator.whitednsProblems(w)
                if (problems.isNotEmpty()) {
                    throw ChainException(listOf(ChainError.WhitednsInvalid(w.id, problems)))
                }
            }
        }
        members.forEach { m ->
            if (m is XrayProfile && m.payload is XrayPayload.Sealed) {
                throw ChainException(listOf(ChainError.SealedPayload(m.id)))
            }
        }

        // -- per-hop work -------------------------------------------------------
        val n = members.size
        val listenPorts = arrayOfNulls<Int>(n)
        val upstreams = arrayOfNulls<PlannedUpstream>(n)
        val configs = arrayOfNulls<HopConfig>(n)

        fun upstreamOf(i: Int): PlannedUpstream? = when {
            i == 0 -> null
            members[i - 1] is PsiphonProfile -> PlannedUpstream.FromHop(i - 1)
            else -> PlannedUpstream.Fixed(SocksEndpoint(port = listenPorts[i - 1]!!))
        }

        // Xray link runs (maximal consecutive runs, length >= 2 collapse).
        val runStartOf = HashMap<Int, Int>() // member index -> run start index
        var i = 0
        while (i < n) {
            if (isXrayLink(members[i])) {
                var j = i
                while (j + 1 < n && isXrayLink(members[j + 1])) j++
                if (j > i) for (k in i..j) runStartOf[k] = i
                i = j + 1
            } else {
                i++
            }
        }

        for (idx in 0 until n) {
            val profile = members[idx]
            val runStart = runStartOf[idx]
            when {
                runStart != null && idx != runStart -> {
                    // Collapsed member: no config, no upstream of its own.
                    // The run's exit member advertises the run's port.
                    configs[idx] = HopConfig.XrayRunMember(runStart)
                    if (runStartOf[idx + 1] != runStart) listenPorts[idx] = listenPorts[runStart]
                }
                runStart != null -> {
                    val upstream = upstreamOf(idx).also { upstreams[idx] = it }
                    // Run start: one config for the whole run; its listener
                    // is the run's single inbound on a fresh port.
                    val runIndices = (idx until n).takeWhile { runStartOf[it] == idx }
                    val runPort = allocator.allocate()
                    listenPorts[idx] = runPort
                    val servers = runIndices.map { parseLink((members[it] as XrayProfile).payload as XrayPayload.Link) }
                    val chainHops = servers.map { XrayChainWiring.ChainHop.Xray(it) }
                    val pending = upstream is PlannedUpstream.FromHop
                    val opts = if (upstream is PlannedUpstream.Fixed) {
                        settings.xrayOptions.copy(dialerSocksPort = upstream.endpoint.port)
                    } else {
                        settings.xrayOptions
                    }
                    configs[idx] = HopConfig.XrayRunStart(
                        json = XrayChainWiring.buildChainHops(chainHops, runPort, opts),
                        memberIndices = runIndices,
                        carrierSplicePending = pending,
                    )
                }
                else -> {
                    val upstream = upstreamOf(idx).also { upstreams[idx] = it }
                    planSingleHop(
                        idx, profile, upstream, settings, allocator, listenPorts, configs,
                        standalone = n == 1,
                        sessionDirName = dirName(isChain, sessionId, idx),
                    )
                }
            }
        }

        val hops = members.mapIndexed { idx, profile ->
            PlannedHop(
                profile = profile,
                kind = profile.kind,
                sessionDirName = dirName(isChain, sessionId, idx),
                index = idx,
                listenPort = listenPorts[idx],
                upstream = upstreams[idx],
                config = configs[idx]!!,
            )
        }

        // -- DNS (§4.6) ---------------------------------------------------------
        val dns = profileDns(members.last()).ifBlank { settings.sessionDns }

        // -- bridge / TUN owner (§4.5 step 6) ------------------------------------
        val exit = members.last()
        val bridge: BridgePlan? = when {
            !isChain && exit is XrayProfile -> null // Xray owns the TUN (hev)
            !isChain && exit is SingBoxProfile && !exit.fragment -> null // own TUN inbound
            else -> {
                val exitUpstream: PlannedUpstream = when (exit) {
                    is PsiphonProfile -> PlannedUpstream.FromHop(n - 1)
                    else -> PlannedUpstream.Fixed(SocksEndpoint(port = listenPorts[n - 1]!!))
                }
                val json = (exitUpstream as? PlannedUpstream.Fixed)?.let { fixed ->
                    BridgeConfigBuilder.bridge(
                        exitSocksPort = fixed.endpoint.port,
                        localPort = BRIDGE_LOCAL_PORT,
                        device = settings.device,
                        dns = dns,
                        tun = !settings.proxyOnly,
                    ).json
                }
                BridgePlan(
                    json = json,
                    localPort = BRIDGE_LOCAL_PORT,
                    exit = exitUpstream,
                    dns = dns,
                    device = settings.device,
                )
            }
        }

        return SessionPlan(
            sessionId = sessionId,
            hops = hops,
            dns = dns,
            routing = settings.routing,
            perApp = settings.perApp,
            proxyOnly = settings.proxyOnly,
            bridge = bridge,
            warnings = warnings,
        )
    }

    // -- one non-run hop ----------------------------------------------------------

    private fun planSingleHop(
        idx: Int,
        profile: Profile,
        upstream: PlannedUpstream?,
        settings: PlanSettings,
        allocator: PortAllocator,
        listenPorts: Array<Int?>,
        configs: Array<HopConfig?>,
        standalone: Boolean,
        sessionDirName: String,
    ) {
        val fixedPort = (upstream as? PlannedUpstream.Fixed)?.endpoint?.port
        val pending = upstream is PlannedUpstream.FromHop
        when (profile) {
            is XrayProfile -> when (val payload = profile.payload) {
                is XrayPayload.Link -> {
                    val port = if (standalone) {
                        BRIDGE_LOCAL_PORT // standalone Xray: its own inbound is the session listener
                    } else {
                        allocator.allocate()
                    }
                    listenPorts[idx] = port
                    val opts = if (fixedPort != null) {
                        settings.xrayOptions.copy(dialerSocksPort = fixedPort)
                    } else {
                        settings.xrayOptions
                    }
                    configs[idx] = HopConfig.XrayJson(
                        json = XrayConfigBuilder.build(parseLink(payload), port, opts),
                        carrierSplicePending = pending,
                    )
                }
                is XrayPayload.RawJson -> {
                    val port = allocator.allocate()
                    listenPorts[idx] = port
                    var json = XrayConfigBuilder.normalizeRawJson(payload.json, port)
                    if (fixedPort != null) json = XrayChainWiring.withCarrierProxy(json, fixedPort)
                    configs[idx] = HopConfig.XrayJson(json, carrierSplicePending = pending)
                }
                is XrayPayload.Sealed -> error("sealed payload reached planning") // guarded above
            }
            is SingBoxProfile -> {
                if (pending) {
                    throw ChainException(listOf(ChainError.DynamicUpstreamUnsupported(profile.id, idx)))
                }
                if (profile.fragment) {
                    val port = allocator.allocate()
                    listenPorts[idx] = port
                    val tuned = SingBoxTuning.apply(profile.json, settings.xrayOptions.singBox)
                    configs[idx] = HopConfig.SingBoxJson(
                        SingBoxHopConfigs.wrapFragment(tuned, port, fixedPort),
                    )
                } else if (standalone) {
                    val prepared = BridgeConfigBuilder.prepareForDevice(profile.json, settings.device)
                    listenPorts[idx] = prepared.socksPort
                    configs[idx] = HopConfig.SingBoxJson(prepared.configJson)
                } else {
                    val port = allocator.allocate()
                    listenPorts[idx] = port
                    configs[idx] = HopConfig.SingBoxJson(
                        SingBoxHopConfigs.wrapFullConfig(profile.json, port, fixedPort),
                    )
                }
            }
            is PsiphonProfile -> {
                val socks = allocator.allocate()
                val http = allocator.allocate()
                listenPorts[idx] = null // runtime-dynamic (§2.5)
                var json = PsiphonConfigBuilder.build(profile, sessionDirName, socks, http)
                if (fixedPort != null) json = PsiphonConfigBuilder.withUpstreamProxy(json, fixedPort)
                configs[idx] = HopConfig.PsiphonJson(
                    json = json,
                    carrierSplicePending = pending,
                    preferredSocksPort = socks,
                    preferredHttpPort = http,
                )
            }
            is AetherProfile -> {
                val bind = allocator.allocate()
                listenPorts[idx] = bind
                val runtime = when {
                    fixedPort != null -> profile.copy(upstream = "socks5://127.0.0.1:$fixedPort")
                    pending -> profile.copy(upstream = "")
                    else -> profile
                }
                val argv = if (AetherCommands.isCustom(runtime)) {
                    // Verbatim mode: inject the chain upstream only when
                    // the command lacks one (a baked --upstream mid-chain
                    // is V8's business; at the entry there is no upstream).
                    val base = AetherCommands.customArguments(runtime, bind)
                    if (fixedPort != null && !base.contains("--upstream")) {
                        base + listOf("--upstream", "socks5://127.0.0.1:$fixedPort")
                    } else {
                        base
                    }
                } else {
                    listOf("--bind", "127.0.0.1:$bind") + AetherFlags.toFlags(runtime) + listOf("--log-level", "info")
                }
                configs[idx] = HopConfig.AetherRuntime(
                    profile = runtime, argv = argv, bindPort = bind, upstreamPending = pending,
                )
            }
            is MihomoProfile -> {
                val port = allocator.allocate()
                listenPorts[idx] = port
                configs[idx] = if (pending) {
                    HopConfig.MihomoYaml(yaml = null, splicePending = true)
                } else {
                    HopConfig.MihomoYaml(
                        yaml = MihomoConfigEditor.prepare(profile, port, fixedPort),
                        splicePending = false,
                    )
                }
            }
            is WhitednsProfile -> {
                val port = allocator.allocate()
                listenPorts[idx] = port
                val rendered = WhitednsConfigRenderer.render(profile, listenPort = port)
                configs[idx] = HopConfig.WhitednsFiles(rendered.configToml, rendered.resolversTxt)
            }
            else -> error("unplannable profile type: ${profile::class.simpleName}")
        }
    }

    // -- helpers --------------------------------------------------------------------

    private fun dirName(isChain: Boolean, sessionId: String, index: Int): String =
        if (isChain) "chains/$sessionId/hop-$index" else "sessions/$sessionId"

    private fun isXrayLink(profile: Profile): Boolean =
        profile is XrayProfile && profile.payload is XrayPayload.Link

    private fun parseLink(payload: XrayPayload.Link) = ConfigParser.parse(payload.link)

    private fun profileDns(profile: Profile): String = when (profile) {
        is XrayProfile -> profile.dns
        is MihomoProfile -> profile.dns
        is WhitednsProfile -> profile.dns
        is AetherProfile -> profile.dns
        else -> ""
    }
}
