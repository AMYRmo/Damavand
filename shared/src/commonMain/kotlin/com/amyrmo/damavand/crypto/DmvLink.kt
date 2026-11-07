package com.amyrmo.damavand.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Text form of a .dmv container.
 *
 *  damavand://<base64url>              the whole (compressed + encrypted) config inside the link
 *  damavand://r.<id>@<host>            short link: the encrypted blob lives on the owner's relay, only the id is in the link
 *
 * Decoding accepts both base64 flavours, with or without padding, and ignores
 * whitespace / line breaks that chat apps add to long links.
 */
@OptIn(ExperimentalEncodingApi::class)
object DmvLink {
    data class Relay(val id: String, val host: String)

    private val RELAY = Regex(
        "(?:damavand)://r\\.([A-Za-z0-9_-]{6,64})@([A-Za-z0-9.-]+(?::\\d{1,5})?)",
        RegexOption.IGNORE_CASE,
    )

    fun findRelay(text: String): Relay? = RELAY.find(text)?.let { m ->
        Relay(m.groupValues[1], m.groupValues[2].trimEnd('.'))
    }

    /** Replaces the first relay link in [text] by [replacement] (keeps any key / note text around it). */
    fun replaceRelay(text: String, replacement: String): String {
        val m = RELAY.find(text) ?: return text
        return text.replaceRange(m.range, replacement)
    }

    fun relayLink(relay: Relay): String = "damavand://r.${relay.id}@${relay.host}"

    fun encode(bytes: ByteArray): String = "damavand://" + toUrlSafe(bytes)

    fun toUrlSafe(bytes: ByteArray): String =
        Base64.Default.encode(bytes).trimEnd('=').replace('+', '-').replace('/', '_')

    private fun isB64(c: Char): Boolean =
        (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c == '+' || c == '/' || c == '_' || c == '-' || c == '='

    private val DMV_MAGIC = "DMV1".encodeToByteArray()

    /**
     * Decodes the first damavand:// payload in [text] if — and only if — it is a .dmv
     * container (DMV1 magic). Plain damavand:// profile links (ZedLink) return null,
     * so callers can fall through to the normal link parser.
     */
    fun findContainer(text: String): ByteArray? {
        val idx = text.indexOf("damavand://", ignoreCase = true)
        if (idx < 0) return null
        val bytes = runCatching { decode(text.substring(idx)) }.getOrNull() ?: return null
        return bytes.takeIf { it.size >= 4 && it.copyOfRange(0, 4).contentEquals(DMV_MAGIC) }
    }

    /** Accepts `damavand://…` or a bare base64 / base64url string. Throws IllegalArgumentException if unreadable. */
    fun decode(input: String): ByteArray {
        var s = input.filterNot { it.isWhitespace() || it == '\u200E' || it == '\u200F' || it == '\u200B' }
        val sep = s.indexOf("://")
        if (sep in 1..8) s = s.substring(sep + 3)
        if ('%' in s) {
            s = s.replace("%2B", "+", ignoreCase = true)
                .replace("%2F", "/", ignoreCase = true)
                .replace("%3D", "=", ignoreCase = true)
        }
        val core = s.takeWhile { isB64(it) }.trimEnd('=').replace('-', '+').replace('_', '/')
        require(core.length >= 16) { "not a locked config" }
        require(core.length % 4 != 1) { "damaged link" }
        val padded = core + "=".repeat((4 - core.length % 4) % 4)
        return Base64.Default.decode(padded)
    }
}
