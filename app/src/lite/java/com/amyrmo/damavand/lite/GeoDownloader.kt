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
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Geo data (geoip.dat + geosite.dat) download-on-demand (Phase 2).
 *
 * The geo DB is NOT bundled in the APK. It is downloaded once from the
 * v2fly releases, checksum-verified before swap-in, and stored in the
 * Xray asset directory (filesDir/assets/) so the core can load it.
 * Connecting is never blocked on geo: when geo is Missing the core runs
 * the minimal config (no geo rules); when Ready, the config gains the
 * geo-based routing rule.
 *
 * Sources (v2fly):
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

    fun refreshState() {
        _state.value = currentState()
    }

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
    fun isReady(): Boolean = geoipFile.exists() && geositeFile.exists()

    /**
     * Download both files (geoip.dat + dlc.dat as geosite.dat), verifying
     * the SHA-256 checksum from the release when it is available, then
     * swap them in. The previous good version stays usable until the new
     * one verifies. Returns true on success.
     */
    suspend fun download(onProgress: (Int) -> Unit = {}): Boolean {
        cancelRequested = false
        val wasReady = isReady()
        _state.value = State.Downloading(0)
        return try {
            val tmpDir = File(context.cacheDir, "geo-tmp").apply { mkdirs() }
            val geoipTmp = File(tmpDir, "geoip.dat")
            val geositeTmp = File(tmpDir, "geosite.dat")
            downloadOne(GEOIP_URL, geoipTmp, GEOIP_SHA_URL) { p ->
                _state.value = State.Downloading((p / 2).coerceIn(0, 100))
                onProgress(p / 2)
            }
            if (cancelRequested) throw InterruptedException("cancelled")
            downloadOne(GEOSITE_URL, geositeTmp, GEOSITE_SHA_URL) { p ->
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
                    "فایل دانلودشده خراب است — دوباره تلاش کنید"
                else ->
                    "دانلود ناموفق بود — اتصال اینترنت را بررسی کنید"
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

    private fun downloadOne(url: String, out: File, shaUrl: String?, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "DamavandLite/1.1.0")
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
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
            val conn = URL(shaUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) return null
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
        private const val GEOIP_URL =
            "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat"
        private const val GEOIP_SHA_URL =
            "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat.sha256sum"
        private const val GEOSITE_URL =
            "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat"
        private const val GEOSITE_SHA_URL =
            "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat.sha256sum"
    }
}
