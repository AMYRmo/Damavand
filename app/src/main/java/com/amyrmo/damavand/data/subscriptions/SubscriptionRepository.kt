package com.amyrmo.damavand.data.subscriptions

import android.content.Context
import com.amyrmo.damavand.model.ProfileJson
import com.amyrmo.damavand.model.Subscription
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * The subscription store (architecture §3.3/§3.5): `subscriptions.json` in
 * filesDir, same versioned-envelope + atomic-write + backup-fallback
 * discipline as the profile store. Fetching/refreshing lives in the
 * subscription service (UI phase); this class is storage only.
 */
class SubscriptionRepository(context: Context) {

    @Serializable
    private data class Envelope(val schema: Int, val subscriptions: List<Subscription>)

    private val dir = context.filesDir
    private val file = File(dir, FILE_NAME)
    private val backup = File(dir, "$FILE_NAME.bak")
    private val tmp = File(dir, "$FILE_NAME.tmp")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var pendingWrite: Job? = null

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions

    private val _loadFault = MutableStateFlow<String?>(null)
    val loadFault: StateFlow<String?> = _loadFault

    @Volatile private var loaded = false

    suspend fun load() {
        if (loaded) return
        loaded = true
        val main = readEnvelope(file)
        if (main != null) {
            _subscriptions.value = main
            return
        }
        val bak = readEnvelope(backup)
        if (bak != null) {
            _subscriptions.value = bak
            _loadFault.value = "subscriptions.json was unreadable; restored the previous generation"
        } else if (file.exists() || backup.exists()) {
            _loadFault.value = "the subscription store could not be read"
        }
    }

    fun subscription(id: String): Subscription? = _subscriptions.value.firstOrNull { it.id == id }

    fun upsert(subscription: Subscription) {
        _subscriptions.value = _subscriptions.value.filterNot { it.id == subscription.id } + subscription
        scheduleWrite()
    }

    fun remove(id: String) {
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        scheduleWrite()
    }

    suspend fun flush() {
        pendingWrite?.join()
        writeNow()
    }

    private fun scheduleWrite() {
        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            delay(DEBOUNCE_MS)
            writeNow()
        }
    }

    private suspend fun writeNow() {
        writeMutex.withLock {
            val snapshot = _subscriptions.value
            runCatching {
                val text = ProfileJson.json.encodeToString(Envelope.serializer(), Envelope(SCHEMA, snapshot))
                tmp.outputStream().use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                if (file.exists()) file.copyTo(backup, overwrite = true)
                check(tmp.renameTo(file)) { "rename failed" }
            }.onFailure {
                _loadFault.value = "the subscription store could not be written: ${it.message}"
            }
        }
    }

    private fun readEnvelope(f: File): List<Subscription>? {
        if (!f.isFile) return null
        return runCatching {
            val envelope = ProfileJson.json.decodeFromString(Envelope.serializer(), f.readText())
            if (envelope.schema != SCHEMA) null else envelope.subscriptions
        }.getOrNull()
    }

    companion object {
        const val FILE_NAME = "subscriptions.json"
        const val SCHEMA = 2
        private const val DEBOUNCE_MS = 250L
    }
}
