package com.amyrmo.damavand.lite

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log lines (3.1.0, PattNG log viewer): a 300-line in-memory ring
 * buffer with timestamps. The VPN service and the core feed it through
 * [addLine]; the Logs tab reads [lines] live.
 */
object AppLog {
    private const val TAG = "Khazar"
    private const val MAX_LINES = 300

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun addLine(text: String) {
        val stamped = "${timeFormat.format(Date())} $text"
        val next = (_lines.value + stamped).takeLast(MAX_LINES)
        _lines.value = next
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun i(tag: String, msg: String) {
        Log.i(TAG, "$tag: $msg")
        addLine("$tag: $msg")
    }

    fun w(tag: String, msg: String) {
        Log.w(TAG, "$tag: $msg")
        addLine("$tag: $msg")
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        Log.e(TAG, "$tag: $msg", e)
        addLine("$tag: $msg${if (e != null) " — ${e.message}" else ""}")
    }
}
