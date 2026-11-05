package com.amyrmo.damavand.data.net

import com.amyrmo.damavand.domain.config.Protocol
import com.amyrmo.damavand.domain.config.ServerConfig
import com.amyrmo.damavand.platform.httpJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * WARP registration as a plain WireGuard device (BPB port, cluster 4:
 * lib/services/warp.dart). Unlike [WarpMasque] (which re-enrolls the device
 * for MASQUE), this keeps the original WireGuard enrollment: one POST to the
 * public v0a4005 endpoint with a fresh X25519 key pair, and the answer is an
 * ordinary WireGuard [ServerConfig] — usable standalone and as a chain hop.
 */
object WarpWireGuard {
    private const val REG_URL = "https://api.cloudflareclient.com/v0a4005/reg"
    const val NAME = "Cloudflare WARP (WireGuard)"

    private val HEADERS = mapOf(
        "User-Agent" to "insomnia/13.0.2",
        "Content-Type" to "application/json",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun register(socksPort: Int? = null): ServerConfig {
        val keys = X25519.generate()
        val body = buildJsonObject {
            put("install_id", "")
            put("fcm_token", "")
            put("tos", WarpMasque.cloudflareTime(System.currentTimeMillis()))
            put("type", "Android")
            put("model", "PC")
            put("locale", "en_US")
            put("warp_enabled", true)
            put("key", keys.publicKey)
        }
        val created = httpJson("POST", REG_URL, HEADERS, body.toString(), socksPort = socksPort)
        if (created.code !in 200..299) throw WarpRegistrationException("Cloudflare answered HTTP ${created.code}")
        return serverFrom(parse(created.body), keys.privateKey)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun serverFrom(account: JsonObject, privateKey: String): ServerConfig {
        val config = account["config"] as? JsonObject
            ?: throw WarpRegistrationException("Cloudflare sent no tunnel config")
        val peer = (config["peers"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw WarpRegistrationException("Cloudflare sent no WireGuard endpoint")
        val publicKey = peer.text("public_key")
            ?: throw WarpRegistrationException("Cloudflare sent no endpoint key")
        val endpoint = peer["endpoint"] as? JsonObject
        val ports = (endpoint?.get("ports") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: listOf(2408)
        // Every offered endpoint (v4 and v6 × each port, BPB's order) rides
        // along as candidates; the builder balances over them best-ping.
        val offered = listOfNotNull(endpoint?.text("v4"), endpoint?.text("v6"))
            .mapNotNull(WarpMasque::hostOf)
            .flatMap { h -> ports.map { p -> if (h.contains(':')) "[$h]:$p" else "$h:$p" } }
        val host = endpoint?.text("v4")?.let(WarpMasque::hostOf)
            ?: endpoint?.text("v6")?.let(WarpMasque::hostOf)
            ?: throw WarpRegistrationException("Cloudflare sent no endpoint address")
        val addresses = (config["interface"] as? JsonObject)?.get("addresses") as? JsonObject
        val local = listOfNotNull(
            (addresses?.text("v4")?.takeIf { it.isNotBlank() } ?: "172.16.0.2").let { if ('/' in it) it else "$it/32" },
            addresses?.text("v6")?.takeIf { it.isNotBlank() }?.let { if ('/' in it) it else "$it/128" },
        )
        val reserved = config.text("client_id")?.let { clientId ->
            runCatching { Base64.decode(clientId) }.getOrNull()
                ?.takeIf { it.size == 3 }
                ?.joinToString(",") { (it.toInt() and 0xff).toString() }
        }
        return ServerConfig(
            protocol = Protocol.WIREGUARD,
            remark = NAME,
            address = host,
            port = ports.first(),
            secretKey = privateKey,
            peerPublicKey = publicKey,
            localAddresses = local,
            reserved = reserved,
            wireguardMtu = 1280,
            wireguardKeepalive = 10,
            candidateAddresses = offered,
        )
    }

    private fun parse(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw WarpRegistrationException("Cloudflare sent an unreadable answer")

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * Curve25519 key pairs, the way `xray wg` makes them (RFC 7748 X25519 with
 * the base point). Ported from BPB's pure-Dart ladder; BigInteger stands in
 * for Dart's bigint arithmetic.
 */
internal object X25519 {
    class KeyPair(val privateKey: String, val publicKey: String)

    @OptIn(ExperimentalEncodingApi::class)
    fun generate(): KeyPair {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        clamp(raw)
        return KeyPair(Base64.encode(raw), Base64.encode(base(raw)))
    }

    private val p: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val a24: BigInteger = BigInteger.valueOf(121665)

    private fun clamp(k: ByteArray) {
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = (k[31].toInt() and 127).toByte()
        k[31] = (k[31].toInt() or 64).toByte()
    }

    private fun base(scalar: ByteArray): ByteArray {
        val u = ByteArray(32)
        u[0] = 9
        return x25519(scalar, u)
    }

    private fun x25519(scalar: ByteArray, uBytes: ByteArray): ByteArray {
        val k = scalar.copyOf()
        clamp(k)
        val kn = decodeLe(k)
        val u = uBytes.copyOf()
        u[31] = (u[31].toInt() and 127).toByte()
        val x1 = decodeLe(u).mod(p)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0
        for (t in 254 downTo 0) {
            val kt = kn.shiftRight(t).and(BigInteger.ONE).toInt()
            swap = swap xor kt
            if (swap == 1) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt
            val a = x2.add(z2).mod(p)
            val aa = a.multiply(a).mod(p)
            val b = x2.subtract(z2).mod(p)
            val bb = b.multiply(b).mod(p)
            val e = aa.subtract(bb).mod(p)
            val c = x3.add(z3).mod(p)
            val d = x3.subtract(z3).mod(p)
            val da = d.multiply(a).mod(p)
            val cb = c.multiply(b).mod(p)
            val daPlusCb = da.add(cb).mod(p)
            x3 = daPlusCb.multiply(daPlusCb).mod(p)
            val diff = da.subtract(cb).mod(p)
            z3 = x1.multiply(diff).multiply(diff).mod(p)
            x2 = aa.multiply(bb).mod(p)
            z2 = e.multiply(aa.add(a24.multiply(e)).mod(p)).mod(p)
        }
        if (swap == 1) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }
        return encodeLe(x2.multiply(z2.modPow(p.subtract(BigInteger.TWO), p)).mod(p))
    }

    private fun decodeLe(bytes: ByteArray): BigInteger {
        var n = BigInteger.ZERO
        for (i in bytes.indices.reversed()) {
            n = n.shiftLeft(8).or(BigInteger.valueOf(bytes[i].toLong() and 0xff))
        }
        return n
    }

    private fun encodeLe(n: BigInteger): ByteArray {
        val out = ByteArray(32)
        var v = n
        val mask = BigInteger.valueOf(0xff)
        for (i in 0 until 32) {
            out[i] = v.and(mask).toInt().toByte()
            v = v.shiftRight(8)
        }
        return out
    }
}
