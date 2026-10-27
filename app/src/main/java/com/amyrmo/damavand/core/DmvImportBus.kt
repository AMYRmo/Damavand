package com.amyrmo.damavand.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pending incoming .dmv import request (from SEND / VIEW intents). */
object DmvImportBus {
    data class Request(val fileName: String, val bytes: ByteArray) {
        override fun equals(other: Any?) = other is Request && other.fileName == fileName &&
            other.bytes.contentEquals(bytes)
        override fun hashCode() = fileName.hashCode() * 31 + bytes.contentHashCode()
    }

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending

    fun request(r: Request) { _pending.value = r }
    fun consume() { _pending.value = null }
}
