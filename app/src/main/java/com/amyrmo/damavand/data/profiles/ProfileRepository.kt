package com.amyrmo.damavand.data.profiles

import android.content.Context
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.ProfileJson
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
 * The profile store (architecture §3.3): two files in filesDir —
 * `profiles.json` (all profiles, chains included) in a versioned envelope
 * `{"schema": 2, "profiles": [ … ]}` — the sealed [Profile] hierarchy IS
 * the disk format, so there is no second storage model.
 *
 * Writes are atomic (temp file → fsync → rename, previous generation kept
 * as `.bak`) and debounced; a corrupt main file falls back to the backup
 * generation and surfaces [loadFault] instead of starting empty — v1's
 * silent-loss mode is not repeated.
 */
class ProfileRepository(context: Context) {

    @Serializable
    private data class Envelope(val schema: Int, val profiles: List<Profile>)

    private val dir = context.filesDir
    private val file = File(dir, FILE_NAME)
    private val backup = File(dir, "$FILE_NAME.bak")
    private val tmp = File(dir, "$FILE_NAME.tmp")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var pendingWrite: Job? = null

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles

    /** Set when the on-disk store could not be read cleanly (fault banner). */
    private val _loadFault = MutableStateFlow<String?>(null)
    val loadFault: StateFlow<String?> = _loadFault

    @Volatile private var loaded = false

    /** Read the store once. Safe to call again; later calls are no-ops. */
    suspend fun load() {
        if (loaded) return
        loaded = true
        val main = readEnvelope(file)
        if (main != null) {
            _profiles.value = main
            return
        }
        val bak = readEnvelope(backup)
        if (bak != null) {
            _profiles.value = bak
            _loadFault.value = "profiles.json was unreadable; restored the previous generation"
        } else if (file.exists() || backup.exists()) {
            _loadFault.value = "the profile store could not be read"
        }
    }

    fun profile(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun upsert(profile: Profile) {
        _profiles.value = _profiles.value.filterNot { it.id == profile.id } + profile
        scheduleWrite()
    }

    fun remove(id: String) {
        _profiles.value = _profiles.value.filterNot { it.id == id }
        scheduleWrite()
    }

    fun replaceAll(profiles: List<Profile>) {
        _profiles.value = profiles
        scheduleWrite()
    }

    /** Flush a pending debounced write now (e.g. on service start). */
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
            val snapshot = _profiles.value
            runCatching {
                val text = ProfileJson.json.encodeToString(Envelope.serializer(), Envelope(SCHEMA, snapshot))
                tmp.outputStream().use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                if (file.exists()) file.copyTo(backup, overwrite = true)
                check(tmp.renameTo(file)) { "rename failed" }
            }.onFailure {
                _loadFault.value = "the profile store could not be written: ${it.message}"
            }
        }
    }

    private fun readEnvelope(f: File): List<Profile>? {
        if (!f.isFile) return null
        return runCatching {
            val envelope = ProfileJson.json.decodeFromString(Envelope.serializer(), f.readText())
            if (envelope.schema != SCHEMA) null else envelope.profiles
        }.getOrNull()
    }

    companion object {
        const val FILE_NAME = "profiles.json"
        const val SCHEMA = 2
        private const val DEBOUNCE_MS = 250L
    }
}
