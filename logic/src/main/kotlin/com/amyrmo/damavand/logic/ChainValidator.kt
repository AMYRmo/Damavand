package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.ChainProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.PsiphonProfile
import com.amyrmo.damavand.model.WhitednsProfile

/**
 * Chain validation (architecture §4.7, rules V1–V9). Errors are typed and
 * exhaustive — the UI renders each variant; warnings are advisory and ride
 * along on the plan. [validateProfiles] covers V2–V9 over an already
 * resolved member list; V1 (existence) is the planner's job, since it owns
 * the lookup.
 */
sealed interface ChainError {
    /** V1 — a member id did not resolve. */
    data class MissingMember(val profileId: String, val position: Int) : ChainError

    /** V2 — chain length outside 2..5. */
    data class TooShort(val size: Int) : ChainError
    data class TooLong(val size: Int) : ChainError

    /** V2 — the same profile appears twice. */
    data class DuplicateMember(val profileId: String) : ChainError

    /** V2 — a member is itself a chain (or an unexpanded AutoSelect). */
    data class NestedChain(val profileId: String, val position: Int) : ChainError

    /** V3 — the hop may not sit at this position (Whitedns entry-only). */
    data class PositionNotAllowed(val profileId: String, val position: Int, val reason: String) : ChainError

    /** V4 — the hop cannot dial through the previous hop's SOCKS. */
    data class SpliceImpossible(val profileId: String, val position: Int) : ChainError

    /** V5 — [needingProfileId] needs UDP but [carrierProfileId] can't carry it. */
    data class UdpNotCarried(val needingProfileId: String, val carrierProfileId: String) : ChainError

    /** V7 — the DNS-tunnel member is itself invalid. */
    data class WhitednsInvalid(val profileId: String, val reasons: List<String>) : ChainError

    /** V8 — a custom-command Aether carries its own --upstream mid-chain. */
    data class CustomCommandUpstream(val profileId: String, val position: Int) : ChainError

    /** A sealed Xray payload reached the planner unopened. */
    data class SealedPayload(val profileId: String) : ChainError

    /**
     * The hop's config cannot be spliced at start time because its
     * generator has no pending form (sing-box hops after a Psiphon hop,
     * whose port is runtime-dynamic).
     */
    data class DynamicUpstreamUnsupported(val profileId: String, val position: Int) : ChainError

    /** The target itself cannot be planned (AutoSelect expansion is :app's job). */
    data class UnplannableTarget(val reason: String) : ChainError
}

/** Advisory validation output (V9 + the Psiphon position note of §4.3). */
sealed interface ChainWarning {
    /** A DNS tunnel is in the chain: expect DNS-tunnel throughput. */
    data object DnsTunnelSpeed : ChainWarning

    /** Four or more hops: latency compounds. */
    data object LatencyLength : ChainWarning

    /** Psiphon sits mid-chain: that segment is TCP-only. */
    data class TcpOnlySegment(val profileId: String) : ChainWarning
}

class ChainException(val errors: List<ChainError>) :
    Exception("invalid chain: " + errors.joinToString("; ") { it.toString() })

data class ValidationResult(val errors: List<ChainError>, val warnings: List<ChainWarning>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object ChainValidator {

    const val MIN_HOPS = 2
    const val MAX_HOPS = 5

    /** V2–V9 over resolved [hops] (entry first). Standalone (size 1) lists
     * are not chain-validated here — the planner only calls this for real
     * chains; the structural rules do not apply to a chain of one. */
    fun validateProfiles(hops: List<Profile>): ValidationResult {
        val errors = mutableListOf<ChainError>()
        val warnings = mutableListOf<ChainWarning>()

        // V2 — shape.
        if (hops.size < MIN_HOPS) errors += ChainError.TooShort(hops.size)
        if (hops.size > MAX_HOPS) errors += ChainError.TooLong(hops.size)
        hops.groupingBy { it.id }.eachCount().forEach { (id, count) ->
            if (count > 1) errors += ChainError.DuplicateMember(id)
        }
        hops.forEachIndexed { i, hop ->
            if (hop is ChainProfile || hop is com.amyrmo.damavand.model.AutoSelectProfile) {
                errors += ChainError.NestedChain(hop.id, i)
            }
        }

        hops.forEachIndexed { i, hop ->
            val caps = CoreCapabilities.of(hop)

            // V3 — position.
            val max = caps.maxPosition
            if (max != null && i > max) {
                errors += ChainError.PositionNotAllowed(
                    hop.id, i,
                    if (hop is WhitednsProfile) {
                        "a DNS tunnel has no upstream-proxy support and can only be the entry hop"
                    } else {
                        "this core may only sit at position $max"
                    },
                )
            }

            // V4 — splice.
            if (i > 0 && !caps.canDialThroughSocks) {
                errors += ChainError.SpliceImpossible(hop.id, i)
            }

            // V5 — UDP continuity (position-aware carriage).
            if (i > 0 && CoreCapabilities.needsUdp(hop) &&
                !CoreCapabilities.carriesUdpAt(hops[i - 1], i - 1)
            ) {
                errors += ChainError.UdpNotCarried(hop.id, hops[i - 1].id)
            }

            // V7 — WhiteDNS member validity.
            if (hop is WhitednsProfile) {
                val reasons = whitednsProblems(hop)
                if (reasons.isNotEmpty()) errors += ChainError.WhitednsInvalid(hop.id, reasons)
            }

            // V8 — custom-command Aether with its own --upstream.
            if (hop is AetherProfile && i > 0 && AetherCommands.isCustom(hop)) {
                val argv = AetherCommands.argumentsOf(hop.command)
                if (argv.drop(1).any { it == "--upstream" || it.startsWith("--upstream=") }) {
                    errors += ChainError.CustomCommandUpstream(hop.id, i)
                }
            }
        }

        // V9 — advisory.
        if (hops.any { it is WhitednsProfile }) warnings += ChainWarning.DnsTunnelSpeed
        if (hops.size >= 4) warnings += ChainWarning.LatencyLength
        hops.forEachIndexed { i, hop ->
            if (hop is PsiphonProfile && i > 0) warnings += ChainWarning.TcpOnlySegment(hop.id)
        }

        return ValidationResult(errors, warnings)
    }

    /** The V7 member checks: ≥1 domain, ≥1 resolver, all resolvers numeric
     * (the product's own rule, v1 MasterDnsProfile), method in 0..5. */
    fun whitednsProblems(profile: WhitednsProfile): List<String> {
        val reasons = mutableListOf<String>()
        if (profile.domains.none { it.isNotBlank() }) reasons += "no tunnel domains"
        val resolvers = profile.resolvers.map { it.trim() }.filter { it.isNotEmpty() }
        if (resolvers.isEmpty()) {
            reasons += "no resolvers"
        } else {
            val bad = resolvers.filterNot { WhitednsResolvers.isNumericResolver(it) }
            if (bad.isNotEmpty()) reasons += "resolvers must be IP literals: ${bad.joinToString(", ")}"
        }
        if (profile.encryptionMethod !in 0..5) {
            reasons += "encryption method ${profile.encryptionMethod} outside 0..5"
        }
        return reasons
    }
}
