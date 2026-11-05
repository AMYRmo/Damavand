package com.amyrmo.damavand.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Authored Xray `finalmask` model, ported from BPB-VPN's mask system
 * (lib/models/settings.dart: Range / Fragment / UdpNoise). One settings-level
 * list of TCP fragment entries plus one list of UDP noise entries; the Xray
 * builder attaches them to proxy outbounds as a `finalmask` stream block.
 * Everything defaults to "nothing authored" so existing behavior is kept
 * unless the user explicitly enables the mask system.
 */

/** A numeric range as Xray writes it: "100" when min == max, else "100-200". */
@Serializable
data class MaskRange(val min: Int = 0, val max: Int = 0) {
    fun toXrayString(): String = if (min == max) "$min" else "$min-$max"

    companion object {
        /** Parses "100" or "100-200"; null when the text is not a valid range. */
        fun parseOrNull(text: String): MaskRange? {
            val parts = text.trim().split('-').map { it.trim() }
            if (parts.size !in 1..2) return null
            val lo = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val hi = if (parts.size == 2) parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null else lo
            return MaskRange(lo, hi)
        }
    }
}

/** One TCP fragment entry of a finalmask (BPB's Fragment). */
@Serializable
data class MaskFragment(
    val packets: FragmentPackets = FragmentPackets.TlsHello,
    val lengths: List<MaskRange> = listOf(MaskRange(100, 200)),
    val delays: List<MaskRange> = listOf(MaskRange(10, 20)),
    val maxSplit: MaskRange? = null,
) {
    fun settingsJson(): JsonObject = buildJsonObject {
        put("packets", packets.value)
        putJsonArray("lengths") { lengths.forEach { add(it.toXrayString()) } }
        putJsonArray("delays") { delays.forEach { add(it.toXrayString()) } }
        maxSplit?.let { put("maxSplit", it.toXrayString()) }
    }

    fun entryJson(): JsonObject = buildJsonObject {
        put("type", "fragment")
        put("settings", settingsJson())
    }
}

/** UDP noise payload kinds (BPB's NoiseType). Names are the stored form. */
enum class NoiseKind { Rand, Str, Hex, Base64, Array, Exp }

/**
 * One UDP noise entry (BPB's UdpNoise), emitted [count] times. The payload
 * field depends on [kind]: Rand uses [size], Array parses [packet] as a
 * comma-separated byte list, the rest send [packet] verbatim.
 */
@Serializable
data class UdpNoise(
    val kind: NoiseKind = NoiseKind.Rand,
    val size: MaskRange = MaskRange(50, 100),
    val packet: String = "",
    val count: Int = 1,
    val delay: MaskRange = MaskRange(1, 5),
) {
    fun arrayBytes(): List<Int> = packet.split(',', ' ', ';')
        .mapNotNull { it.trim().toIntOrNull() }
        .map { it.coerceIn(0, 255) }

    /** Expands into [count] xray noise entries, exactly like BPB's _udpNoises. */
    fun entriesJson(): List<JsonObject> {
        val delayText = delay.toXrayString()
        return (0 until count.coerceIn(1, 100)).map {
            buildJsonObject {
                when (kind) {
                    NoiseKind.Rand -> {
                        put("rand", size.toXrayString())
                        put("randRange", "0-255")
                        put("delay", delayText)
                    }
                    NoiseKind.Array -> {
                        put("type", "array")
                        putJsonArray("packet") { arrayBytes().forEach { b -> add(b) } }
                        put("delay", delayText)
                    }
                    else -> {
                        put("type", kind.name.lowercase())
                        put("packet", packet)
                        put("delay", delayText)
                    }
                }
            }
        }
    }
}

/** Builds the `finalmask` JSON object from authored entries (BPB's _finalMask). */
object FinalMaskJson {
    fun build(
        fragments: List<MaskFragment>,
        noises: List<UdpNoise>,
        resetMin: Int,
        resetMax: Int,
    ): JsonObject? {
        if (fragments.isEmpty() && noises.isEmpty()) return null
        return buildJsonObject {
            if (fragments.isNotEmpty()) {
                putJsonArray("tcp") { fragments.forEach { add(it.entryJson()) } }
            }
            if (noises.isNotEmpty()) {
                putJsonArray("udp") {
                    addJsonObject {
                        put("type", "noise")
                        putJsonObject("settings") {
                            put("reset", MaskRange(resetMin, resetMax).toXrayString())
                            putJsonArray("noise") {
                                noises.forEach { n -> n.entriesJson().forEach { add(it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}
