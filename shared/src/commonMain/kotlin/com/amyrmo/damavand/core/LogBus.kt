package com.amyrmo.damavand.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

object LogBus {
    private const val CAPACITY = 2_000

    private const val SLACK = 256

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    var source: ((active: Boolean) -> Unit)? = null

    private var watchers = 0

    private val _ownTags = MutableStateFlow<Set<String>>(emptySet())
    val ownTags: StateFlow<Set<String>> = _ownTags.asStateFlow()

    fun noteOwnTag(tag: String) {
        if (tag !in _ownTags.value) _ownTags.update { it + tag }
    }

    // Appends are staged and published in batches (E4). Publishing is
    // the expensive part — it copies the whole list (up to ~2,256
    // lines) and emits a StateFlow update — so doing it per core log
    // line made a chatty line rate cost O(lines) per line. Staging is a
    // small-list append; the published list keeps the same capacity,
    // the same merge ordering and the same content, just refreshed per
    // batch instead of per line. attach() and snapshot() flush first,
    // so a reader never misses the staged tail.
    private const val FLUSH_WHEN_WATCHED = 8

    private const val FLUSH_WHEN_UNWATCHED = 50

    private val _staged = MutableStateFlow<List<String>>(emptyList())

    fun append(line: String) {
        appendAll(listOf(line))
    }

    fun appendAll(newLines: List<String>) {
        if (newLines.isEmpty()) return
        val staged = _staged.updateAndGet { it + newLines }
        val threshold = if (watchers > 0) FLUSH_WHEN_WATCHED else FLUSH_WHEN_UNWATCHED
        if (staged.size >= threshold) flush()
    }

    /** Publishes any staged lines to [lines] now. */
    fun flush() {
        val batch = _staged.getAndUpdate { emptyList() }
        if (batch.isEmpty()) return
        _lines.update { prev ->
            val first = stampOf(batch.first())
            val last = prev.asReversed().firstNotNullOfOrNull { stampOf(it) }
            if (first == null || last == null || first >= last) trim(prev + batch)
            else trim(mergeByStamp(prev, batch))
        }
    }

    private fun stampOf(line: String): String? =
        if (line.length >= 18 && line[2] == '-' && line[5] == ' ' && line[8] == ':' && line[14] == '.') {
            line.substring(0, 18)
        } else {
            null
        }

    private fun mergeByStamp(a: List<String>, b: List<String>): List<String> {
        val out = ArrayList<String>(a.size + b.size)
        var i = 0
        var j = 0
        var keyA = ""
        var keyB = ""
        while (i < a.size || j < b.size) {
            if (i < a.size) keyA = stampOf(a[i]) ?: keyA
            if (j < b.size) keyB = stampOf(b[j]) ?: keyB
            when {
                j >= b.size -> out += a[i++]
                i >= a.size -> out += b[j++]
                keyB < keyA -> out += b[j++]
                else -> out += a[i++]
            }
        }
        return out
    }

    private fun trim(next: List<String>): List<String> =
        if (next.size > CAPACITY + SLACK) next.subList(next.size - CAPACITY, next.size) else next

    fun attach() {
        flush()
        watchers++
        if (watchers == 1) source?.invoke(true)
    }

    fun detach() {
        if (watchers > 0) watchers--
        if (watchers == 0) source?.invoke(false)
    }

    fun clear() {
        _staged.value = emptyList()
        _lines.value = emptyList()
    }

    fun snapshot(): String {
        flush()
        return _lines.value.joinToString("\n")
    }
}
