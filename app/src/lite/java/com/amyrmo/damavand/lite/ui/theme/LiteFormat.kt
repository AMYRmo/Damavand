package com.amyrmo.damavand.lite.ui.theme

/** Persian digits + one format everywhere (design spec §8.1). */
object LiteFormat {

    /**
     * 3.2.0: false in the English UI — digits and units stay Latin.
     * Set by [com.amyrmo.damavand.lite.LocaleHelper] when the locale
     * is applied; defaults to Persian (the app's first language).
     */
    var persianDigits: Boolean = true

    private val LATIN_DIGITS = "0123456789"
    private val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    /** Convert Latin digits to Persian digits and '.' to '٫' — a
     *  no-op in the English UI (persianDigits = false). */
    fun toPersian(text: String): String {
        if (!persianDigits) return text
        val sb = StringBuilder(text.length)
        for (c in text) {
            when {
                c in LATIN_DIGITS -> sb.append(PERSIAN_DIGITS[LATIN_DIGITS.indexOf(c)])
                c == '.' -> sb.append('٫')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun oneDecimal(value: Double): String {
        val rounded = (value * 10).toLong() / 10.0
        val s = if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", rounded)
        }
        return toPersian(s)
    }

    /** Bytes/sec → "۱۲٫۴ مگابیت" (Mbps) or "۱٫۲ گیگابیت" (≥ 1000 Mbps). */
    fun formatSpeed(bytesPerSec: Long): String {
        val mbps = bytesPerSec * 8.0 / 1_000_000.0
        if (!persianDigits) {
            return if (mbps >= 1000) "${oneDecimal(mbps / 1000)} Gbps"
            else "${oneDecimal(mbps)} Mbps"
        }
        return if (mbps >= 1000) "${oneDecimal(mbps / 1000)} گیگابیت"
        else "${oneDecimal(mbps)} مگابیت"
    }

    /** Bytes → "۳۲۴ کیلوبایت" (< 1 MB), "۱۸٫۲ مگابایت" or "۱٫۲ گیگابایت" (≥ 1024 MB).
     *  3.1.2: small live sessions used to round to "۰ مگابایت" and look dead.
     *  3.2.30: 1024-based — panels (3x-ui, Remnawave) count a "GB" as 1024^3 bytes,
     *  so 100 GiB must read "۱۰۰ گیگابایت", not "۱۰۷٫۳". Speeds stay decimal (Mbps). */
    fun formatVolume(bytes: Long): String {
        val kb = bytes / 1024.0
        if (!persianDigits) {
            if (kb < 1024) return "${oneDecimal(kb)} KB"
            val mb = kb / 1024.0
            return if (mb >= 1024) "${oneDecimal(mb / 1024)} GB"
            else "${oneDecimal(mb)} MB"
        }
        if (kb < 1024) return "${oneDecimal(kb)} کیلوبایت"
        val mb = kb / 1024.0
        return if (mb >= 1024) "${oneDecimal(mb / 1024)} گیگابایت"
        else "${oneDecimal(mb)} مگابایت"
    }

    /** Ping ms → "۸۱ ms" (integer). */
    fun formatPing(ms: Long): String = "${toPersian(ms.toString())} ms"

    /** Percent 0–100 — "۷۳٪" in Persian, "73%" in English. */
    fun formatPercent(percent: Int): String =
        "${toPersian(percent.toString())}${if (persianDigits) "٪" else "%"}"

    /** Seconds → "MM:SS" (or "H:MM:SS" past one hour). The caller
     *  converts digits via [toPersian] (the Portal door does). */
    fun formatElapsed(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
        else "%02d:%02d".format(m, sec)
    }

    /** Bytes/sec → (value, unit): ("12.4", "Mbps") / ("1.2", "Gbps").
     *  The value is raw Latin digits — the caller converts via
     *  [toPersian] (the Portal FlowMeter does). */
    fun formatRate(bytesPerSec: Long): Pair<String, String> {
        val mbps = bytesPerSec * 8.0 / 1_000_000.0
        val raw = if (mbps >= 1000) mbps / 1000 else mbps
        val rounded = (raw * 10).toLong() / 10.0
        val value = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", rounded)
        val unit = if (mbps >= 1000) {
            if (persianDigits) "گیگابیت" else "Gbps"
        } else {
            if (persianDigits) "مگابیت" else "Mbps"
        }
        return value to unit
    }
}
