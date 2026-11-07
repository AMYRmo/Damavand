package com.amyrmo.damavand.data.config

import com.amyrmo.damavand.crypto.DmvCrypto
import com.amyrmo.damavand.crypto.DmvLink
import com.amyrmo.damavand.crypto.DmvWrongPasswordException
import com.amyrmo.damavand.platform.currentTimeMillis
import com.amyrmo.damavand.platform.httpJson

/** The relay holds a one-time key for this link and none was supplied. */
class RelayKeyRequiredException : Exception("A private key is required for this link")

/** The one-time key of this link was already activated on another device. */
class RelayAlreadyClaimedException : Exception("This link was already activated on another device")

/**
 * Thrown by text import when a damavand:// locked link needs the interactive locked-import
 * dialog (password entry, device check or a relay download) instead of a silent import.
 */
class LockedDmvPendingException(val bytes: ByteArray?, val relay: DmvLink.Relay?) :
    Exception("locked dmv import pending")

/**
 * Client for the optional "short link" relay (see tools/dmv-relay-worker.js).
 * The relay only ever stores the already-encrypted .dmv blob; the link carries just an id and the relay host.
 */
object DmvRelay {
    private val ID_REGEX = Regex("\"id\"\\s*:\\s*\"([A-Za-z0-9_-]{6,64})\"")
    private val HOST_REGEX = Regex("[A-Za-z0-9.-]+(:\\d{1,5})?")
    private val IPV4_REGEX = Regex("\\d{1,3}(\\.\\d{1,3}){3}")
    private const val MAX_BODY_CHARS = 6_000_000

    /** "https://dmv.example.workers.dev/" -> "dmv.example.workers.dev"; null when it is not a usable public host. */
    fun hostOf(endpoint: String): String? {
        val host = endpoint.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        return host.takeIf { validHost(it) }
    }

    fun validHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253 || !HOST_REGEX.matches(host)) return false
        val name = host.substringBefore(':').lowercase()
        if ('.' !in name || name.startsWith('.') || name.endsWith('.')) return false
        if (name == "localhost" || name.endsWith(".local") || name.endsWith(".internal")) return false
        if (IPV4_REGEX.matches(name)) return false
        return true
    }

    suspend fun upload(endpoint: String, token: String?, blob: ByteArray, expiresAt: Long?, userKey: String? = null): DmvLink.Relay {
        val host = hostOf(endpoint) ?: throw IllegalArgumentException("Invalid relay server address")
        val ttl = expiresAt?.let { ((it - currentTimeMillis()) / 1000L).coerceAtLeast(60L) + 3600L }
        val url = "https://$host/put" + (if (ttl != null) "?ttl=$ttl" else "")
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "text/plain"
        headers["User-Agent"] = "Damavand"
        if (!token.isNullOrBlank()) headers["X-DMV-Token"] = token.trim()
        // The relay keeps only a hash of the key and releases the blob to the first device that proves it knows the key.
        if (!userKey.isNullOrBlank()) headers["X-DMV-Proof"] = DmvCrypto.claimProof(userKey)
        val res = httpJson("POST", url, headers, DmvLink.toUrlSafe(blob), 15_000, 30_000, null)
        check(res.code in 200..299) { "HTTP ${res.code}" }
        val id = ID_REGEX.find(res.body)?.groupValues?.get(1) ?: error("Invalid response from relay server")
        return DmvLink.Relay(id, host)
    }

    suspend fun download(relay: DmvLink.Relay, userKey: String?, deviceId: String): ByteArray {
        require(validHost(relay.host)) { "Invalid relay host in link" }
        val res = if (userKey.isNullOrBlank()) {
            httpJson(
                "GET", "https://${relay.host}/get/${relay.id}",
                mapOf("User-Agent" to "Damavand"), null, 15_000, 30_000, null,
            )
        } else {
            httpJson(
                "POST", "https://${relay.host}/claim/${relay.id}",
                mapOf("User-Agent" to "Damavand", "Content-Type" to "application/json"),
                "{\"proof\":\"${DmvCrypto.claimProof(userKey)}\",\"device\":\"$deviceId\"}",
                15_000, 30_000, null,
            )
        }
        when (res.code) {
            in 200..299 -> Unit
            401 -> throw RelayKeyRequiredException()
            403 -> throw DmvWrongPasswordException()
            404 -> error("Link not found or expired")
            409 -> throw RelayAlreadyClaimedException()
            else -> error("HTTP ${res.code}")
        }
        require(res.body.length <= MAX_BODY_CHARS) { "response too large" }
        return DmvLink.decode(res.body)
    }
}
