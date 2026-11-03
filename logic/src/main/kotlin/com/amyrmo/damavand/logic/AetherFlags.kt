package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AETHER_ECH_DNS
import com.amyrmo.damavand.model.AETHER_ECH_DOMAIN
import com.amyrmo.damavand.model.AETHER_MASQUE_SNI
import com.amyrmo.damavand.model.AetherEndpoint
import com.amyrmo.damavand.model.AetherFingerprint
import com.amyrmo.damavand.model.AetherIpVersion
import com.amyrmo.damavand.model.AetherLink
import com.amyrmo.damavand.model.AetherObfuscation
import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AetherProtocol
import com.amyrmo.damavand.model.AetherPsiphon
import com.amyrmo.damavand.model.AetherPsiphonCdnSet
import com.amyrmo.damavand.model.AetherPsiphonMode
import com.amyrmo.damavand.model.AetherRange
import com.amyrmo.damavand.model.AetherScanMode
import com.amyrmo.damavand.model.AetherTor
import com.amyrmo.damavand.model.AetherTorBridges
import com.amyrmo.damavand.model.AetherTorRelays
import com.amyrmo.damavand.model.AetherTransport
import com.amyrmo.damavand.model.masqueOverHttp2

/**
 * Flag generation for the Aether core (architecture §3.4 `AetherFlags`) —
 * v1's `AetherProfile.toFlags`/`commandPreview`, moved out of the model so
 * the model stays data-only. The output follows PattNG's
 * AetherCoreManager.buildArguments flag-for-flag (minus the multi-port
 * loopback plumbing, which the single --bind bridge replaces).
 *
 * Tested invariant (§6.2): a session's flags always contain
 * `--quick-reconnect` and never `--no-quick-reconnect` — scanning is a
 * property of a throwaway finder run, never of a session (build-#78).
 */
object AetherFlags {

    fun toFlags(profile: AetherProfile): List<String> {
        val proto = AetherProtocol.fromString(profile.protocol)
        val transportEnum = AetherTransport.fromString(profile.transport)
        val torEnum = AetherTor.fromString(profile.tor)
        val psiphonEnum = AetherPsiphon.fromString(profile.psiphon)
        return buildList {
            if (psiphonEnum != AetherPsiphon.ONLY && torEnum != AetherTor.ONLY) {
                addAll(listOf("--protocol", proto.core))
                if (proto == AetherProtocol.GOOL) add("--gool-classic")
                addAll(listOf("--scan", AetherScanMode.fromString(profile.scanMode).type))
                val overHttp2 = masqueOverHttp2(proto, transportEnum, torEnum, psiphonEnum)
                AetherObfuscation.fromString(profile.obfuscation)
                    .takeUnless { it == AetherObfuscation.AUTO || overHttp2 }
                    ?.let { addAll(listOf("--noize", it.type)) }
                addAll(listOf("--ip", AetherIpVersion.fromString(profile.ipVersion).type))
                profile.dns.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--dns", it)) }
                profile.exitLoc.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--exit-loc", it)) }

                if (proto.overMasque) {
                    addAll(listOf("--masque-sni", profile.masqueSni.trim().ifEmpty { AETHER_MASQUE_SNI }))
                }
                if (proto.overMasque && transportEnum == AetherTransport.HTTP2) {
                    add("--h2")
                    if (profile.fragment) {
                        add("--fragment")
                        AetherRange.parse(profile.fragmentSize, AetherRange.FRAGMENT_SIZE)
                            ?.let { addAll(listOf("--fragment-size", it.toString())) }
                        AetherRange.parse(profile.fragmentDelay, AetherRange.FRAGMENT_DELAY)
                            ?.let { addAll(listOf("--fragment-delay", it.toString())) }
                    }
                }
                if (proto.overMasque && profile.ech) {
                    addAll(listOf("--ech", "auto"))
                    addAll(listOf("--ech-dns", profile.echDns.trim().ifEmpty { AETHER_ECH_DNS }))
                    addAll(listOf("--ech-domain", profile.echDomain.trim().ifEmpty { AETHER_ECH_DOMAIN }))
                }
                if (proto.overMasque) addAll(AetherFingerprint.fromString(profile.fingerprint).arguments)

                // Hops are gated per hop, not by the shared `scanning`
                // flag: setting only the outer hop is a legitimate "dial
                // this one, scan the other" shape, and coupling them
                // discarded the set hop entirely (Narcic gates each hop
                // on its own emptiness).
                if (proto == AetherProtocol.WG_OVER_MASQUE) {
                    AetherEndpoint.parse(profile.wiwOuter)?.let { addAll(listOf("--peer", it.toString())) }
                    AetherEndpoint.parse(profile.wiwInner)?.let { addAll(listOf("--gool-peer", it.toString())) }
                } else if (proto.twoHops) {
                    val hop = if (proto == AetherProtocol.MIM) "--mim" else "--wiw"
                    val outer = AetherEndpoint.parse(profile.wiwOuter)
                    val inner = AetherEndpoint.parse(profile.wiwInner)
                    outer?.let { addAll(listOf("$hop-outer", it.toString())) }
                    inner?.let { addAll(listOf("$hop-inner", it.toString())) }
                    if (outer == null && inner == null) add("$hop-scan")
                } else if (!profile.scanning) {
                    AetherEndpoint.of(profile.address, profile.port)?.let { addAll(listOf("--peer", it.toString())) }
                }

                // A session always quick-reconnects. In PattNG and Narcic
                // the scan flag is a property of the *run*, not of the
                // profile: only a throwaway endpoint-scan run (which
                // serves no session) gets --no-quick-reconnect. Keying
                // the flag off a blank endpoint, as v1 once did, made
                // every scan-mode session ignore the core's saved
                // last-working gateway and re-hunt from scratch.
                add("--quick-reconnect")
            }

            when (torEnum) {
                AetherTor.OFF -> {}
                AetherTor.CHAIN -> add("--tor")
                AetherTor.REVERSE -> add("--tor-reverse")
                AetherTor.ONLY -> add("--tor-only")
            }
            if (torEnum != AetherTor.OFF) {
                when (AetherTorBridges.fromString(profile.torBridges)) {
                    AetherTorBridges.AUTO -> {}
                    AetherTorBridges.FIRST -> add("--tor-bridges")
                    AetherTorBridges.NEVER -> add("--no-tor-bridges")
                    AetherTorBridges.OWN ->
                        profile.torBridgeLines.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { addAll(listOf("--tor-bridge", it)) }
                }
                val bridges = AetherTorBridges.fromString(profile.torBridges)
                if (bridges == AetherTorBridges.AUTO || bridges == AetherTorBridges.FIRST) {
                    AetherTorRelays.fromString(profile.torRelays).takeUnless { it == AetherTorRelays.AUTO }
                        ?.let { addAll(listOf("--tor-relays", it.type)) }
                }
            }

            when (psiphonEnum) {
                AetherPsiphon.OFF -> {}
                AetherPsiphon.CHAIN -> add("--psiphon")
                AetherPsiphon.REVERSE -> add("--psiphon-reverse")
                AetherPsiphon.ONLY -> add("--psiphon-only")
            }
            if (psiphonEnum != AetherPsiphon.OFF) {
                val shape = AetherPsiphonMode.fromString(profile.psiphonMode)
                addAll(listOf("--psiphon-mode", shape.type))
                val cdnIps = profile.psiphonCdnIps.trim().takeIf { it.isNotEmpty() && shape != AetherPsiphonMode.DIRECT }
                cdnIps?.let { addAll(listOf("--psiphon-cdn-ips", it)) }
                if (cdnIps != null) profile.psiphonCdnSni.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--psiphon-cdn-sni", it)) }
                if (shape != AetherPsiphonMode.DIRECT) {
                    AetherPsiphonCdnSet.join(AetherPsiphonCdnSet.parse(profile.psiphonCdnSets))
                        ?.let { addAll(listOf("--psiphon-cdn-sets", it)) }
                }
                profile.psiphonRegion.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--psiphon-region", it)) }
                // The bundled list, unless the profile wants Psiphon to fetch a fresh one before it dials anything.
                if (profile.psiphonBundledList) addAll(listOf(AetherLink.PSIPHON_SERVER_ENTRIES, AetherLink.SHIPPED_LIST))
            }
            profile.upstream.trim().takeIf { it.isNotEmpty() }?.let { addAll(listOf("--upstream", it)) }
            profile.extraArgs.trim().takeIf { it.isNotEmpty() }?.let { addAll(it.split(Regex("\\s+"))) }
        }
    }

    /** The "Aether command" preview line, like PattNG shows. The log level matches the session's (info). */
    fun commandPreview(profile: AetherProfile, bindPort: Int): String =
        (listOf("aether", "--bind", "127.0.0.1:$bindPort") + toFlags(profile) + listOf("--log-level", "info")).joinToString(" ")
}
