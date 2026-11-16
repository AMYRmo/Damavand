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

    /** Bytes → "۳۲۴ کیلوبایت" (< 1 MB), "۱۸٫۲ مگابایت" or "۱٫۲ گیگابایت" (≥ 1000 MB).
     *  3.1.2: small live sessions used to round to "۰ مگابایت" and look dead. */
    fun formatVolume(bytes: Long): String {
        val kb = bytes / 1_000.0
        if (!persianDigits) {
            if (kb < 1000) return "${oneDecimal(kb)} KB"
            val mb = kb / 1000.0
            return if (mb >= 1000) "${oneDecimal(mb / 1000)} GB"
            else "${oneDecimal(mb)} MB"
        }
        if (kb < 1000) return "${oneDecimal(kb)} کیلوبایت"
        val mb = kb / 1000.0
        return if (mb >= 1000) "${oneDecimal(mb / 1000)} گیگابایت"
        else "${oneDecimal(mb)} مگابایت"
    }

    /** Ping ms → "۸۱ ms" (integer). */
    fun formatPing(ms: Long): String = "${toPersian(ms.toString())} ms"

    /** Percent 0–100 — "۷۳٪" in Persian, "73%" in English. */
    fun formatPercent(percent: Int): String =
        "${toPersian(percent.toString())}${if (persianDigits) "٪" else "%"}"
}
