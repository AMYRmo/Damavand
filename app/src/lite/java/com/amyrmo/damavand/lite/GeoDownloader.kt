package com.amyrmo.damavand.lite

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Geo data (geoip.dat + geosite.dat).
 *
 * 3.0.0: the official v2fly files ARE bundled in the APK (app/src/lite/
 * assets) — the app copies them into the Xray asset directory
 * (filesDir/assets/) on first use, so geo works with no download at
 * all. The download below stays as the UPDATE path (a newer release
 * overwrites the bundled files, checksum-verified). Connecting is never
 * blocked on geo: when geo is Missing the core runs the minimal config
 * (no geo rules); when Ready, the config gains the geo-based rule.
 *
 * Download sources (v2fly):
 * - geoip.dat: https://github.com/v2fly/geoip/releases/latest/download/geoip.dat
 * - geosite.dat: https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat
 *   (dlc.dat IS the geosite.dat file, renamed on save)
 */
class GeoDownloader(private val context: Context) {

    sealed interface State {
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data class Ready(val version: String) : State
        data class UpdateAvailable(val version: String, val newVersion: String) : State
        data class Error(val reason: String, val wasReady: Boolean) : State
    }

    @Serializable
    private data class Meta(val version: String, val downloadedAt: Long)

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val geoDir: File get() = File(context.filesDir, "assets")
    private val geoipFile: File get() = File(geoDir, "geoip.dat")
    private val geositeFile: File get() = File(geoDir, "geosite.dat")
    private val metaFile: File get() = File(context.filesDir, "geo-meta.json")

    @Volatile
    private var cancelRequested = false

    fun cancel() {
        cancelRequested = true
    }

    /**
     * Import a user-supplied geo file (3.2.0, PattNG): copies the picked
     * file over geoip.dat / geosite.dat in the geo dir and marks the set
     * as custom. The other file (bundled or downloaded) stays as-is.
     */
    fun importFile(uri: android.net.Uri, kind: String): Boolean {
        return try {
            val target = when (kind) {
                "geoip" -> geoipFile
                "geosite" -> geositeFile
                else -> return false
            }
            geoDir.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (target.length() == 0L) return false
            writeMeta(Meta(version = "custom", downloadedAt = System.currentTimeMillis()))
            refreshState()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun refreshState() {
        ensureBundled()
        _state.value = currentState()
    }

    /**
     * Copy the geo files bundled in the APK (3.0.0) into the core asset
     * dir when the dir does not have them yet. The bundled files are the
     * official v2fly release, already checksum-verified at build time,
     * so no network and no verification is needed here. A later
     * download() overwrites them with a newer release. Returns true
     * when the files are present afterwards.
     */
    fun ensureBundled(): Boolean {
        if (filesPresent()) return true
        return try {
            geoDir.mkdirs()
            if (!geoipFile.exists()) {
                context.assets.open("geoip.dat").use { input ->
                    FileOutputStream(geoipFile).use { input.copyTo(it) }
                }
            }
            if (!geositeFile.exists()) {
                context.assets.open("geosite.dat").use { input ->
                    FileOutputStream(geositeFile).use { input.copyTo(it) }
                }
            }
            if (filesPresent()) {
                if (readMeta() == null) {
                    writeMeta(Meta(version = "bundled", downloadedAt = System.currentTimeMillis()))
                }
                _state.value = State.Ready("bundled")
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun filesPresent(): Boolean = geoipFile.exists() && geositeFile.exists()

    private fun currentState(): State {
        val meta = readMeta()
        return if (geoipFile.exists() && geositeFile.exists() && meta != null) {
            State.Ready(meta.version)
        } else {
            State.Missing
        }
    }

    private fun readMeta(): Meta? {
        return try {
            if (!metaFile.exists()) null else json.decodeFromString<Meta>(metaFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun writeMeta(meta: Meta) {
        try {
            metaFile.writeText(json.encodeToString(meta))
        } catch (_: Exception) {
        }
    }

    /** True when both geo files are present (the core may use geo rules). */
    fun isReady(): Boolean {
        if (filesPresent()) return true
        // First run after install: the APK bundles the files, so copy
        // them over before answering (the service asks this at connect).
        return ensureBundled()
    }

    /**
     * Download both files (geoip.dat + dlc.dat as geosite.dat), verifying
     * the SHA-256 checksum from the release when it is available, then
     * swap them in. The previous good version stays usable until the new
     * one verifies. Returns true on success.
     *
     * 2.0.1: each file is tried from jsDelivr first (Loyalsoldier's
     * release branch, byte-identical to the v2fly release) and then
     * from GitHub — on-device 2.0.0 could not reach github.com at all
     * without a VPN, so a single GitHub-only source meant the download
     * could never succeed. Read timeout is 120 s: the files are ~12–24 MB.
     */
    suspend fun download(onProgress: (Int) -> Unit = {}): Boolean {
        cancelRequested = false
        val wasReady = isReady()
        _state.value = State.Downloading(0)
        return try {
            val tmpDir = File(context.cacheDir, "geo-tmp").apply { mkdirs() }
            val geoipTmp = File(tmpDir, "geoip.dat")
            val geositeTmp = File(tmpDir, "geosite.dat")
            downloadOne(GEOIP_SOURCES, geoipTmp) { p ->
                _state.value = State.Downloading((p / 2).coerceIn(0, 100))
                onProgress(p / 2)
            }
            if (cancelRequested) throw InterruptedException("cancelled")
            downloadOne(GEOSITE_SOURCES, geositeTmp) { p ->
                _state.value = State.Downloading((50 + p / 2).coerceIn(0, 100))
                onProgress(50 + p / 2)
            }
            if (cancelRequested) throw InterruptedException("cancelled")
            geoDir.mkdirs()
            geoipTmp.copyTo(geoipFile, overwrite = true)
            geositeTmp.copyTo(geositeFile, overwrite = true)
            val version = java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.US
            ).format(java.util.Date())
            writeMeta(Meta(version = version, downloadedAt = System.currentTimeMillis()))
            _state.value = State.Ready(version)
            true
        } catch (e: InterruptedException) {
            cleanupPartial()
            _state.value = if (wasReady) {
                val meta = readMeta()
                if (meta != null) State.Ready(meta.version) else State.Missing
            } else {
                State.Missing
            }
            false
        } catch (e: Exception) {
            cleanupPartial()
            val reason = when {
                e.message?.contains("checksum", ignoreCase = true) == true ->
                    context.getString(R.string.geo_file_corrupt)
                else ->
                    context.getString(R.string.geo_download_failed)
            }
            _state.value = State.Error(reason, wasReady)
            false
        }
    }

    private fun cleanupPartial() {
        try {
            File(context.cacheDir, "geo-tmp").deleteRecursively()
        } catch (_: Exception) {
        }
    }

    /**
     * Download one file, trying each (url, shaUrl) source in order.
     * A source with a null shaUrl is a CDN mirror of the same release
     * content (jsDelivr serves the GitHub release bytes unchanged), so
     * no checksum is available there — verification runs only for the
     * GitHub source. Throws when every source fails.
     */
    private fun downloadOne(
        sources: List<Pair<String, String?>>,
        out: File,
        onProgress: (Int) -> Unit
    ) {
        var lastError: Exception? = null
        for ((url, shaUrl) in sources) {
            if (cancelRequested) throw InterruptedException("cancelled")
            try {
                downloadFrom(url, out, shaUrl, onProgress)
                return
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: java.io.IOException("download failed")
    }

    private fun downloadFrom(url: String, out: File, shaUrl: String?, onProgress: (Int) -> Unit) {
        // HttpFetch tries the default route, then every non-VPN network
        // underneath it (2.0.2): geo must download whether the VPN is
        // on or off, even when one path is broken.
        val conn = HttpFetch.openConnection(context, url, 20_000, 120_000, "DamavandLite/2.0.2")
            ?: throw java.io.IOException("connection failed")
        val total = conn.contentLengthLong
        val digest = MessageDigest.getInstance("SHA-256")
        FileOutputStream(out).use { fos ->
            conn.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } != -1) {
                    if (cancelRequested) throw InterruptedException("cancelled")
                    fos.write(buf, 0, read)
                    digest.update(buf, 0, read)
                    done += read
                    if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                }
            }
        }
        conn.disconnect()
        // Verify checksum when the release publishes one.
        val expected = fetchChecksum(shaUrl)
        if (expected != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                throw java.io.IOException("checksum mismatch")
            }
        }
        if (out.length() == 0L) throw java.io.IOException("empty file")
    }

    private fun fetchChecksum(shaUrl: String?): String? {
        if (shaUrl.isNullOrBlank()) return null
        return try {
            val conn = HttpFetch.openConnection(context, shaUrl, 10_000, 10_000, "DamavandLite/2.0.2")
                ?: return null
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            // Format: "<sha256>  <filename>" (first token).
            text.trim().split(Regex("\\s+")).firstOrNull()
                ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * Per file: (download URL, checksum URL or null). First entry is
         * the jsDelivr mirror of the v2fly release (Loyalsoldier's
         * release branch, same bytes), second is GitHub itself.
         * - geoip.dat: https://github.com/v2fly/geoip/releases/latest/download/geoip.dat
         * - geosite.dat: https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat
         *   (dlc.dat IS the geosite.dat file, renamed on save)
         */
        private val GEOIP_SOURCES = listOf(
            "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat" to null,
            "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat" to
                "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat.sha256sum",
        )
        private val GEOSITE_SOURCES = listOf(
            "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat" to null,
            "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat" to
                "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat.sha256sum",
        )
    }
}
