package com.amyrmo.damavand.lite.ui.theme

/** Persian digits + one format everywhere (design spec §8.1). */
object LiteFormat {

    private val LATIN_DIGITS = "0123456789"
    private val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    /** Convert Latin digits to Persian digits and '.' to '٫'. */
    fun toPersian(text: String): String {
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
        return if (mbps >= 1000) "${oneDecimal(mbps / 1000)} گیگابیت"
        else "${oneDecimal(mbps)} مگابیت"
    }

    /** Bytes → "۱۸٫۲ مگابایت" or "۱٫۲ گیگابایت" (≥ 1000 MB). */
    fun formatVolume(bytes: Long): String {
        val mb = bytes / 1_000_000.0
        return if (mb >= 1000) "${oneDecimal(mb / 1000)} گیگابایت"
        else "${oneDecimal(mb)} مگابایت"
    }

    /** Ping ms → "۸۱ ms" (integer). */
    fun formatPing(ms: Long): String = "${toPersian(ms.toString())} ms"

    /** Percent 0–100 → "۷۳٪" (integer). */
    fun formatPercent(percent: Int): String = "${toPersian(percent.toString())}٪"
}
