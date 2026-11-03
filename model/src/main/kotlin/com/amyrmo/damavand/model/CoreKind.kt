package com.amyrmo.damavand.model

/**
 * The engine kind of a profile / session hop (architecture §2.4 rule 4, §3.1).
 *
 * This enum is the ONLY representation of engine kind in v2: profile →
 * planner → plan → adapter resolution is by type, end to end. There are no
 * `KIND_*` string constants and no kind strings in intents (the v1 design
 * caused a total-connect failure, commit `4a573fa`).
 *
 * [CHAIN] and [AUTO] are plan-level kinds: a [Profile] variant exists for
 * them (ChainProfile / AutoSelectProfile) but no core adapter implements
 * them — the planner expands them into hops of the real core kinds.
 */
enum class CoreKind {
    XRAY,
    SINGBOX,
    PSIPHON,
    AETHER,
    MIHOMO,
    WHITEDNS,
    CHAIN,
    AUTO,
}
