package com.amyrmo.damavand.core

import com.amyrmo.damavand.crypto.ZsxMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VaultImportBus {
    class Pending(val bytes: ByteArray, val metadata: ZsxMetadata)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun request(bytes: ByteArray, metadata: ZsxMetadata) {
        _pending.value = Pending(bytes, metadata)
    }

    fun clear() {
        _pending.value = null
    }
}
