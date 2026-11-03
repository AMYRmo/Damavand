package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.model.Profile

/**
 * The session plan types (architecture §2.2). A standalone profile is a
 * chain of length 1: the same shapes, the same runner, no special case.
 *
 * Two fields extend the §2.2 sketch, both forced by §2.4/§2.5 and recorded
 * here rather than hidden:
 *  - [SessionPlan.bridge] carries the TUN owner's config (§2.4: the bridge
 *    plan is part of what the planner produces, §4.5 step 6);
 *  - [SessionPlan.warnings] carries the V9 advisory output of validation
 *    (§4.7), which the UI shows before connecting.
 */

data class SocksEndpoint(val host: String = "127.0.0.1", val port: Int)

enum class RoutingMode { GLOBAL, RULE_BASED, IRAN_BYPASS }

sealed interface PerAppSelection {
    data object All : PerAppSelection
    data class Include(val packages: Set<String>) : PerAppSelection
    data class Exclude(val packages: Set<String>) : PerAppSelection
}

data class SessionPlan(
    val sessionId: String,
    val hops: List<PlannedHop>,
    /** Profile-DNS choice for the bridge ("" = bridge default 1.1.1.1). */
    val dns: String,
    val routing: RoutingMode,
    val perApp: PerAppSelection,
    /** Proxy-only mode: no TUN; the bridge (if any) is loopback-only. */
    val proxyOnly: Boolean,
    /** The TUN-owning bridge, or null when a hop owns the TUN itself. */
    val bridge: BridgePlan?,
    val warnings: List<ChainWarning>,
)

/**
 * The bridge's half of the plan. [json] is null exactly when the exit hop's
 * listener port is only known at runtime (a Psiphon exit, §2.5 rule 3): the
 * runner then builds the bridge through [BridgeConfigBuilder.bridge] with
 * [exit] resolved, [localPort], [device] and the plan's DNS string.
 */
data class BridgePlan(
    val json: String?,
    val localPort: Int,
    val exit: PlannedUpstream,
    val dns: String,
    val device: BridgeConfigBuilder.DeviceOptions,
)

/** Where a hop's (or the bridge's) upstream endpoint comes from. */
sealed interface PlannedUpstream {
    /** Known at plan time. */
    data class Fixed(val endpoint: SocksEndpoint) : PlannedUpstream

    /** The listener of hop [hopIndex], adopted when that hop reports ready. */
    data class FromHop(val hopIndex: Int) : PlannedUpstream
}

data class PlannedHop(
    val profile: Profile,
    val kind: CoreKind,
    /** Isolated per-hop directory name under the session directory. */
    val sessionDirName: String,
    val index: Int,
    /**
     * The loopback SOCKS port this hop listens on. Null only for hops
     * collapsed into an Xray run ([HopConfig.XrayRunMember]) and for
     * Psiphon, whose port the core chooses and the app adopts (§2.5).
     */
    val listenPort: Int?,
    /** The previous hop as this hop's carrier; null at the entry. */
    val upstream: PlannedUpstream?,
    val config: HopConfig,
)

/**
 * The per-core start payload the session layer (§2) consumes. Variants
 * carrying a `*Pending`/`splicePending` flag mean the previous hop's port
 * was not knowable at plan time (it follows a Psiphon hop): at start time
 * the runner resolves the actual endpoint and applies the named splice
 * from :logic before handing the config to the adapter.
 */
sealed interface HopConfig {
    /** A complete Xray config (standalone hop, or one hop of a chain). */
    data class XrayJson(val json: String, val carrierSplicePending: Boolean) : HopConfig

    /**
     * The first profile of a collapsed Xray run: one config for members
     * [memberIndices] (indices into [SessionPlan.hops]); the exit member's
     * listener is the run's listener.
     */
    data class XrayRunStart(
        val json: String,
        val memberIndices: List<Int>,
        val carrierSplicePending: Boolean,
    ) : HopConfig

    /** A non-first member of a collapsed Xray run; nothing to start. */
    data class XrayRunMember(val runStartIndex: Int) : HopConfig

    /** A complete sing-box config (fragment wrapper or prepared full config). */
    data class SingBoxJson(val json: String) : HopConfig

    /**
     * A Psiphon client config. [preferredSocksPort]/[preferredHttpPort] are
     * the planner's request; the core may bind elsewhere and the runner
     * adopts the reported ports (§2.5 rule 3).
     */
    data class PsiphonJson(
        val json: String,
        val carrierSplicePending: Boolean,
        val preferredSocksPort: Int,
        val preferredHttpPort: Int,
    ) : HopConfig

    /**
     * Aether has no config file: the runner stages [profile] (a runtime
     * copy — for chained hops its `upstream` field carries the carrier URL,
     * or is set at start time when [upstreamPending]) plus these flags.
     */
    data class AetherRuntime(
        val profile: com.amyrmo.damavand.model.AetherProfile,
        val argv: List<String>,
        val bindPort: Int,
        val upstreamPending: Boolean,
    ) : HopConfig

    /** A prepared Mihomo YAML; null [yaml] while a splice is pending. */
    data class MihomoYaml(val yaml: String?, val splicePending: Boolean) : HopConfig

    /** The two WhiteDNS engine files, rendered for the allocated port. */
    data class WhitednsFiles(val configToml: String, val resolversTxt: String) : HopConfig
}
