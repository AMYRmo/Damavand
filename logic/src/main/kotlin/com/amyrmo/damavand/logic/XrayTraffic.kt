package com.amyrmo.damavand.logic

/**
 * Parsing of the Xray core's outbound traffic counters — v1's
 * `XrayController.trafficDeltaOf`, extracted into `:logic` so the string
 * format is pinned by unit tests instead of living inside an Android
 * controller.
 *
 * The core reports `tag,direction,bytes` triples joined by `;`
 * (direction `downlink`/`uplink`). The session total is the `proxy`
 * outbound plus any auto-select member outbounds (`proxy@<index>`, v1's
 * AutoSelectTags). Infrastructure tags (direct, block, dns, api,
 * fragment) are never meters. When the proxy side reports nothing but
 * other outbounds moved traffic, the busiest outbound's counters are
 * returned — v1's fallback, kept deliberately: some generated configs
 * name the egress outbound differently, and showing its real counters
 * beats showing zero.
 */
object XrayTraffic {

    private val NOT_A_METER = setOf("direct", "block", "blocked", "dns-out", "dns_out", "api", "fragment")

    /** (downlinkBytes, uplinkBytes) for the session; (0, 0) for null/garbage input. */
    fun trafficDeltaOf(stats: String?): Pair<Long, Long> {
        val raw = stats ?: return 0L to 0L
        var down = 0L
        var up = 0L

        val byTag = HashMap<String, LongArray>()
        for (entry in raw.split(';')) {
            if (entry.isEmpty()) continue
            val parts = entry.split(',')
            if (parts.size != 3) continue
            val value = parts[2].toLongOrNull() ?: continue
            if (parts[0] !in NOT_A_METER) {
                val slot = byTag.getOrPut(parts[0]) { LongArray(2) }
                when (parts[1]) {
                    "downlink" -> slot[0] += value
                    "uplink" -> slot[1] += value
                }
            }

            if (parts[0] != "proxy" && !isAutoSelectMember(parts[0])) continue
            when (parts[1]) {
                "downlink" -> down += value
                "uplink" -> up += value
            }
        }
        if (down == 0L && up == 0L && byTag.isNotEmpty()) {
            val busiest = byTag.values.maxByOrNull { it[0] + it[1] }
            if (busiest != null) return busiest[0] to busiest[1]
        }
        return down to up
    }

    /** Auto-select member outbound tags are `proxy@<index>` (v1 AutoSelectTags). */
    fun isAutoSelectMember(tag: String): Boolean {
        val prefix = "proxy@"
        return tag.length > prefix.length && tag.startsWith(prefix) &&
            tag.substring(prefix.length).all { it.isDigit() }
    }
}
