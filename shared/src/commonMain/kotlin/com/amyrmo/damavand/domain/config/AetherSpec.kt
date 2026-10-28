package com.amyrmo.damavand.domain.config

/**
 * PattNG's Aether vocabulary, ported 1:1 from patterniha/PattNG
 * (V2rayNG/app/src/main/java/com/v2ray/ang/enums/AetherOption.kt and dto/).
 * Every field maps onto a flag of the patterniha/Aether core binary (v2.4.0+).
 */

/** A WARP protocol: [core] is the word the core's --protocol takes for it. */
enum class AetherProtocol(val type: String, val core: String = type) {
    MASQUE("masque"),
    WIREGUARD("wg"),
    GOOL("gool"),
    MIM("mim"),
    WG_OVER_MASQUE("wg-over-masque", "gool");

    val overMasque: Boolean get() = this == MASQUE || this == MIM || this == WG_OVER_MASQUE
    val twoHops: Boolean get() = this == GOOL || this == MIM || this == WG_OVER_MASQUE
    val distinctHops: Boolean get() = this == GOOL || this == MIM

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: WIREGUARD
    }
}

enum class AetherTransport(val type: String) {
    HTTP3("h3"),
    HTTP2("h2");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: HTTP3
    }
}

enum class AetherScanMode(val type: String) {
    TURBO("turbo"),
    BALANCED("balanced"),
    THOROUGH("thorough"),
    VERIFIED("verified"),
    IRONCLAD("ironclad");

    companion object {
        /** The name the verified mode had before aether 2.1; old links still carry it. */
        const val STEALTH = "stealth"

        fun fromString(type: String?) =
            entries.find { it.type == type } ?: if (type == STEALTH) VERIFIED else BALANCED
    }
}

/** The obfuscation profile, named the way the core names it. */
enum class AetherObfuscation(val type: String) {
    AUTO("auto"),
    OFF("off"),
    LIGHT("light"),
    FIREWALL("firewall"),
    BALANCED("balanced"),
    GFW("gfw"),
    AGGRESSIVE("aggressive");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

enum class AetherIpVersion(val type: String) {
    V4("v4"),
    V6("v6"),
    DUAL("both");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: V4
    }
}

/** Where Psiphon stands in the tunnel of a profile. */
enum class AetherPsiphon(val type: String) {
    OFF("off"),
    CHAIN("chain"),
    REVERSE("reverse"),
    ONLY("only");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: OFF
    }
}

/** The lists of CDN edges built into Psiphon's client. */
enum class AetherPsiphonCdnSet(val type: String) {
    CLOUDFLARE("cloudflare"),
    FASTLY("fastly"),
    CLOUDFRONT("cloudfront"),
    AKAMAI("psiphon-akamai"),
    BUNNY("psiphon-bunny"),
    VERCEL("vercel"),
    GITHUB("github"),
    CURATED("curated-fronting"),
    LEGACY("legacy-android-overrides");

    companion object {
        fun parse(text: String?): List<AetherPsiphonCdnSet> {
            val named = text.orEmpty().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }.toSet()
            return entries.filter { it.type in named }
        }

        fun join(sets: Collection<AetherPsiphonCdnSet>): String? =
            entries.filter { it in sets }.joinToString(",") { it.type }.ifEmpty { null }
    }
}

enum class AetherPsiphonMode(val type: String) {
    AUTO("auto"),
    CDN("cdn"),
    DIRECT("direct");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

/** Where Tor stands in the tunnel of a profile. */
enum class AetherTor(val type: String) {
    OFF("off"),
    CHAIN("chain"),
    REVERSE("reverse"),
    ONLY("only");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: OFF
    }
}

/** When Tor turns to bridges. */
enum class AetherTorBridges(val type: String) {
    AUTO("auto"),
    FIRST("first"),
    NEVER("never"),
    OWN("own");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

/** Where Tor's fetched bridges come from. */
enum class AetherTorRelays(val type: String) {
    AUTO("auto"),
    ONLY("only"),
    OFF("off");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

/** Which WARP keys a registration gets. */
enum class AetherKeyKind(val type: String, val register: String) {
    ALL("all", "all"),
    WIREGUARD("wg", "wg"),
    MASQUE("masque", "masque"),
    GOOL("gool", "gool-classic"),
    MIM("mim", "mim"),
    WG_OVER_MASQUE("wg-over-masque", "gool");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: ALL

        fun ofRegister(word: String?): AetherKeyKind? = when (word?.trim()?.lowercase()) {
            "all" -> ALL
            "wg", "wireguard", "warp" -> WIREGUARD
            "masque" -> MASQUE
            "gool-classic", "wiw", "warp-in-warp" -> GOOL
            "mim", "masque-in-masque" -> MIM
            "gool", "wg-over-masque" -> WG_OVER_MASQUE
            else -> null
        }
    }
}

/**
 * The ClientHello of the TLS handshakes of the core, after a captured client.
 * Ported from PattNG's AetherFingerprint: CHROME / CHROME_120 / FIREFOX /
 * SEMI_PYTHON / GO, each naming its suites, groups and signature algorithms.
 */
enum class AetherFingerprint(
    val type: String,
    val ciphers: String,
    val grease: Boolean,
    val groups: String,
    val sigalgs: String,
    val tls13Ciphers: String? = null,
    val keyShares: String? = null,
    val greaseSigalgs: Boolean = false,
    val certCompression: String? = null,
    val alps: String? = null,
    val trustAnchors: String? = null,
    val serverPadding: Int? = null,
    val echGrease: Boolean = false,
    val ocspStapling: Boolean = true,
    val sct: Boolean = true,
    val sessionTicket: Boolean = true,
    val permuteExtensions: Boolean = true,
) {
    CHROME(
        "chrome",
        "ALL:!aPSK:!ECDSA+SHA1:!3DES",
        true,
        "X25519MLKEM768:X25519:P-256:P-384",
        sigalgs = "mldsa44:mldsa65:mldsa87:$CHROME_EIGHT_SIGALGS",
        greaseSigalgs = true,
        certCompression = "brotli",
        alps = "new",
        trustAnchors = CHROME_TRUST_ANCHORS,
        serverPadding = 6000,
        echGrease = true,
    ),
    CHROME_120(
        "chrome-120",
        "ALL:!aPSK:!ECDSA+SHA1:!3DES",
        true,
        "X25519:P-256:P-384",
        sigalgs = CHROME_EIGHT_SIGALGS,
        certCompression = "brotli",
        alps = "old",
        echGrease = true,
    ),
    FIREFOX(
        "firefox",
        "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:" +
            "ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-SHA:ECDHE-RSA-AES256-SHA:" +
            "AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA:AES256-SHA",
        false,
        "X25519MLKEM768:X25519:P-256:P-384:P-521",
        sigalgs = "ecdsa_secp256r1_sha256:ecdsa_secp384r1_sha384:ecdsa_secp521r1_sha512:rsa_pss_rsae_sha256:" +
            "rsa_pss_rsae_sha384:rsa_pss_rsae_sha512:rsa_pkcs1_sha256:rsa_pkcs1_sha384:rsa_pkcs1_sha512:ecdsa_sha1:" +
            "rsa_pkcs1_sha1",
        tls13Ciphers = "TLS_AES_128_GCM_SHA256:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_256_GCM_SHA384",
        keyShares = "X25519MLKEM768:X25519:P-256",
        certCompression = "zlib:brotli:zstd",
        echGrease = true,
        sessionTicket = false,
        permuteExtensions = false,
    ),
    SEMI_PYTHON(
        "semi-python",
        "ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:" +
            "ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA:" +
            "ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA256",
        false,
        "X25519MLKEM768:X25519:P-256:P-384:P-521",
        sigalgs = "mldsa65:mldsa87:mldsa44:ecdsa_secp256r1_sha256:ecdsa_secp384r1_sha384:ecdsa_secp521r1_sha512:" +
            "ed25519:rsa_pss_rsae_sha256:rsa_pss_rsae_sha384:rsa_pss_rsae_sha512:rsa_pkcs1_sha256:rsa_pkcs1_sha384:" +
            "rsa_pkcs1_sha512",
        tls13Ciphers = "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256",
        ocspStapling = false,
        sct = false,
        permuteExtensions = false,
    ),
    GO(
        "go",
        "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:" +
            "ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES128-SHA:ECDHE-RSA-AES128-SHA:" +
            "ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA",
        false,
        "X25519MLKEM768:X25519:P-256:P-384:P-521",
        sigalgs = "mldsa44:mldsa65:mldsa87:rsa_pss_rsae_sha256:ecdsa_secp256r1_sha256:ed25519:rsa_pss_rsae_sha384:" +
            "rsa_pss_rsae_sha512:rsa_pkcs1_sha256:rsa_pkcs1_sha384:rsa_pkcs1_sha512:ecdsa_secp384r1_sha384:" +
            "ecdsa_secp521r1_sha512",
        sessionTicket = false,
        permuteExtensions = false,
    );

    /** The core flags of this fingerprint. */
    val arguments: List<String>
        get() = buildList {
            addAll(listOf("--tls-ciphers", ciphers))
            tls13Ciphers?.let { addAll(listOf("--tls13-ciphers", it)) }
            addAll(listOf("--tls-groups", groups))
            keyShares?.let { addAll(listOf("--tls-key-shares", it)) }
            addAll(listOf("--tls-sigalgs", sigalgs))
            certCompression?.let { addAll(listOf("--tls-cert-compression", it)) }
            alps?.let { addAll(listOf("--tls-alps", it)) }
            trustAnchors?.let { addAll(listOf("--tls-trust-anchors", it)) }
            serverPadding?.let { addAll(listOf("--tls-server-padding", it.toString())) }
            if (!grease) add("--disable-grease")
            if (greaseSigalgs) add("--enable-grease-sigalgs")
            if (echGrease) add("--enable-ech-grease")
            if (!ocspStapling) add("--disable-ocsp-stapling")
            if (!sct) add("--disable-sct")
            if (!sessionTicket) add("--disable-session-ticket")
            if (!permuteExtensions) add("--disable-extension-permutation")
        }

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: CHROME
    }
}

/** A `min-max` range, as --fragment-size and --fragment-delay take them. */
data class AetherRange(val min: Int, val max: Int) {
    override fun toString(): String = if (min == max) "$min" else "$min-$max"

    companion object {
        val FRAGMENT_SIZE = 1..4096
        val FRAGMENT_DELAY = 0..1000

        fun parse(text: String?, bounds: IntRange): AetherRange? {
            val parts = text?.trim()?.split('-') ?: return null
            if (parts.size !in 1..2) return null
            val values = parts.map { part -> part.trim().toIntOrNull()?.takeIf { it in bounds } ?: return null }
            return AetherRange(values.min(), values.max())
        }
    }
}

/** An `ip:port` endpoint, IPv4 or bracketed IPv6, canonicalized the way PattNG does. */
data class AetherEndpoint(val host: String, val port: Int) {
    override fun toString(): String = if (':' in host) "[$host]:$port" else "$host:$port"

    companion object {
        fun of(host: String?, port: String?): AetherEndpoint? {
            val address = host?.trim()?.removeSurrounding("[", "]")?.let(::canonicalAddress) ?: return null
            val number = port?.trim()?.let(::decimal)?.takeIf { it in 1..65535 } ?: return null
            return AetherEndpoint(address, number)
        }

        fun parse(value: String?): AetherEndpoint? {
            val text = value?.trim().orEmpty()
            val separator = text.lastIndexOf(':')
            if (separator <= 0) return null
            val host = text.substring(0, separator)
            if (':' in host && !(host.startsWith('[') && host.endsWith(']'))) return null
            return of(host, text.substring(separator + 1))
        }

        private fun canonicalAddress(address: String): String? =
            if (':' in address) {
                ipv6Groups(address)?.let { groups ->
                    groups.joinToString(":", prefix = "[", postfix = "]") { "%x".format(it) }
                }
            } else {
                ipv4Octets(address)?.joinToString(".")
            }

        private fun decimal(text: String): Int? {
            if (text.isEmpty() || text.length > 5) return null
            return text.fold(0) { value, ch -> value * 10 + (ch.digitToIntOrNull() ?: return null) }
        }

        private fun ipv4Octets(address: String): List<Int>? {
            val parts = address.split('.')
            if (parts.size != 4) return null
            return parts.map { part ->
                if (part.length !in 1..3 || (part.length > 1 && part.first().digitToIntOrNull() == 0)) return null
                decimal(part)?.takeIf { it <= 255 } ?: return null
            }
        }

        private fun ipv6Groups(address: String): List<Int>? {
            val halves = address.split("::")
            if (halves.size > 2) return null
            val compressed = halves.size == 2
            val head = hexGroups(halves[0], allowIpv4 = !compressed) ?: return null
            val tail = if (compressed) hexGroups(halves[1], allowIpv4 = true) ?: return null else emptyList()
            val missing = 8 - head.size - tail.size
            if (if (compressed) missing < 1 else missing != 0) return null
            return head + List(missing) { 0 } + tail
        }

        private fun hexGroups(section: String, allowIpv4: Boolean): List<Int>? {
            if (section.isEmpty()) return emptyList()
            val pieces = section.split(':')
            val out = mutableListOf<Int>()
            pieces.forEachIndexed { index, piece ->
                if (piece.isEmpty()) return null
                if (piece.contains('.')) {
                    if (!allowIpv4 || index != pieces.lastIndex) return null
                    val v4 = ipv4Octets(piece) ?: return null
                    out.add((v4[0] shl 8) or v4[1])
                    out.add((v4[2] shl 8) or v4[3])
                } else {
                    if (piece.length > 4) return null
                    out.add(piece.toIntOrNull(16) ?: return null)
                }
            }
            return out
        }
    }
}

/** Chrome's eight signature algorithms. */
const val CHROME_EIGHT_SIGALGS = "ecdsa_secp256r1_sha256:rsa_pss_rsae_sha256:rsa_pkcs1_sha256:" +
    "ecdsa_secp384r1_sha384:rsa_pss_rsae_sha384:rsa_pkcs1_sha384:rsa_pss_rsae_sha512:rsa_pkcs1_sha512"

/** The trust anchors Chrome's ClientHello asks for, in dotted decimal, in its order. */
const val CHROME_TRUST_ANCHORS = "44947.2.1:44947.2.6:44947.2.13:44947.2.14:44947.2.15:44947.2.18:44947.2.19:" +
    "44947.2.20:52580.200109.1.7:52580.200109.1.8:52580.200109.1.9:52580.200109.1.10:52580.200109.1.11:" +
    "52580.200109.1.12:52580.200109.1.13:52580.200109.1.18:52580.200109.1.19:11129.9.1:11129.9.4:11129.9.5:" +
    "11129.9.6:11129.9.7:11129.9.8:11129.9.10:11129.9.11:11129.9.12:11129.9.13:11129.9.15"

/** The default server name of the MASQUE handshakes. */
const val AETHER_MASQUE_SNI = "zapu.dev"

/** The default resolver and domain of Encrypted Client Hello. */
const val AETHER_ECH_DNS = "https://192.168.4.6/dns-query {h3}"
const val AETHER_ECH_DOMAIN = "CPZC6gAYJ5liCQAgc3BpZGUuc3BsaXNoLm5ldAEBIAEhAQAgBggAhwEAAQA="

/** Whether a tunnel over MASQUE runs over HTTP/2 (chosen so, or forced by reverse carriers). */
fun masqueOverHttp2(protocol: AetherProtocol, transport: AetherTransport, tor: AetherTor, psiphon: AetherPsiphon): Boolean =
    protocol.overMasque && (transport == AetherTransport.HTTP2 || tor == AetherTor.REVERSE || psiphon == AetherPsiphon.REVERSE)
