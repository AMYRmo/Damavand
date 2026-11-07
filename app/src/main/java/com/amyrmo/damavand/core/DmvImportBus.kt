package com.amyrmo.damavand.core

import com.amyrmo.damavand.crypto.DmvLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pending incoming .dmv import request (from SEND / VIEW intents, deep links or pasted damavand:// locked links). */
object DmvImportBus {
    data class Request(
        val fileName: String,
        val bytes: ByteArray?,
        val relay: DmvLink.Relay? = null,
    ) {
        override fun equals(other: Any?) = other is Request && other.fileName == fileName &&
            other.relay == relay &&
            ((other.bytes == null && bytes == null) ||
                (other.bytes != null && bytes != null && other.bytes.contentEquals(bytes)))
        override fun hashCode() = fileName.hashCode() * 31 + (bytes?.contentHashCode() ?: 0) * 31 + relay.hashCode()
    }

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending

    fun request(r: Request) { _pending.value = r }
    fun consume() { _pending.value = null }
}
