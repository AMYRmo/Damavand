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
 * 3.2.20: the official v2fly files are NOT bundled in the APK any
 * more — they were 26 MB inside it, and the app copied them into the
 * Xray asset directory on first use, so they sat on the phone twice.
 * They download on demand instead (Settings → Geo, or the update
 * path below), from jsDelivr first and GitHub second, checksum-
 * verified. Connecting is never blocked on geo: when geo is Missing
 * the core runs the minimal config (no geo rules); when Ready, the
 * config gains the geo-based rule.
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
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false
            // Validate BEFORE replacing the good file (issue #96): a
            // wrong file used to overwrite the working geo data and
            // break every geo-routed connect until re-downloaded.
            if (bytes.isEmpty() || !isValidProtobuf(bytes)) return false
            target.writeBytes(bytes)
            writeMeta(Meta(version = "custom", downloadedAt = System.currentTimeMillis()))
            refreshState()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Minimal protobuf validity check (issue #96): walk the bytes as a
     * sequence of fields (tag varint + wire type) and require the walk
     * to end exactly at EOF. Both real geo files (GeoIPList /
     * GeoSiteList) start with a length-delimited field 1 (0x0A); a
     * text/HTML error page or a truncated download fails this.
     */
    private fun isValidProtobuf(bytes: ByteArray): Boolean {
        if (bytes.size < 8 || bytes[0] != 0x0A.toByte()) return false
        var pos = 0
        while (pos < bytes.size) {
            var tag = 0L
            var shift = 0
            while (true) {
                if (pos >= bytes.size || shift >= 64) return false
                val b = bytes[pos++].toInt() and 0xFF
                tag = tag or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            if (tag == 0L) return false
            when ((tag and 0x7).toInt()) {
                0 -> {
                    while (true) {
                        if (pos >= bytes.size) return false
                        if (bytes[pos++].toInt() and 0x80 == 0) break
                    }
                }
                1 -> pos += 8
                2 -> {
                    var len = 0L
                    var s2 = 0
                    while (true) {
                        if (pos >= bytes.size || s2 >= 64) return false
                        val b = bytes[pos++].toInt() and 0xFF
                        len = len or ((b and 0x7F).toLong() shl s2)
                        if (b and 0x80 == 0) break
                        s2 += 7
                    }
                    if (len < 0 || len > bytes.size - pos) return false
                    pos += len.toInt()
                }
                5 -> pos += 4
                else -> return false
            }
            if (pos > bytes.size) return false
        }
        return pos == bytes.size
    }

    /**
     * Download one asset file from a URL the user gave (3.2.11, PattNG UserAssetUrlActivity):
     * geoip.dat / geosite.dat go over the files in the geo dir, psiphon_servers.dat over the copy in
     * the asset dir — but only once the download reads as the asset it claims to be: a geo file must
     * be non-empty, a Psiphon list must unpack and verify (PsiphonServerList.unpack). The previous
     * good file stays until the new one verifies. Returns true on success.
     */
    fun downloadFromUrl(url: String, kind: String): Boolean {
        cancelRequested = false
        return try {
            val tmpDir = File(context.cacheDir, "geo-tmp").apply { mkdirs() }
            val tmp = File(tmpDir, "asset-url.tmp")
            downloadFrom(url, tmp, null) {}
            when (kind) {
                "geoip", "geosite" -> {
                    // Validate BEFORE replacing the good file (issue
                    // #96): the previous good file stays until the new
                    // one proves to be a real geo file.
                    val bytes = tmp.readBytes()
                    if (bytes.isEmpty() || !isValidProtobuf(bytes)) return false
                    val target = if (kind == "geoip") geoipFile else geositeFile
                    geoDir.mkdirs()
                    tmp.copyTo(target, overwrite = true)
                    writeMeta(Meta(version = "custom", downloadedAt = System.currentTimeMillis()))
                    refreshState()
                }
                "psiphon" -> {
                    // The list must unpack and verify before it replaces the copy in use.
                    com.amyrmo.damavand.lite.aether.PsiphonServerList.unpack(
                        tmp.readBytes(),
                        com.amyrmo.damavand.lite.aether.PsiphonServerList.SIGNING_KEY,
                    )
                    geoDir.mkdirs()
                    val copy = File(geoDir, com.amyrmo.damavand.lite.aether.PsiphonServerList.SERVERS_DAT)
                    tmp.copyTo(copy, overwrite = true)
                    // A download from now beats the bundled list until a bundle published after it ships.
                    copy.setLastModified(System.currentTimeMillis())
                }
                else -> return false
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            cleanupPartial()
        }
    }

    fun refreshState() {
        _state.value = currentState()
    }

    /**
     * Copy the Psiphon server list bundled in the APK (3.2.11, PattNG
     * PsiphonServerList) into the asset dir when the bundled list goes
     * over the copy there: no copy yet, or the bundled list was
     * published after the copy was made (and the copy is not one the
     * user picked). The copy's time is set to the list's publication
     * time, so a later bundle is newer and an earlier one is not. A
     * no-op when the copy is already current.
     */
    fun ensurePsiphonList(): Boolean {
        return try {
            val copy = File(geoDir, com.amyrmo.damavand.lite.aether.PsiphonServerList.SERVERS_DAT)
            val stamp = try {
                context.assets.open(com.amyrmo.damavand.lite.aether.PsiphonServerList.SERVERS_STAMP)
                    .bufferedReader().readText().trim()
            } catch (_: Exception) {
                null
            }
            val publishedAt = com.amyrmo.damavand.lite.aether.PsiphonServerList.publishedAt(stamp)
            if (!com.amyrmo.damavand.lite.aether.PsiphonServerList.bundledListGoesOver(copy, publishedAt, keptByUser = false)) {
                return copy.isFile
            }
            geoDir.mkdirs()
            context.assets.open(com.amyrmo.damavand.lite.aether.PsiphonServerList.SERVERS_DAT).use { input ->
                FileOutputStream(copy).use { input.copyTo(it) }
            }
            if (publishedAt > 0) copy.setLastModified(publishedAt)
            copy.isFile
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when the geo files are already in the core asset dir (3.2.20:
     * nothing is bundled any more, so this only answers whether a
     * download — or an import — put them there). The download in
     * Settings is what fills the dir now.
     */
    private fun ensurePresent(): Boolean = filesPresent()

    private fun filesPresent(): Boolean = geoipFile.exists() && geositeFile.exists()

    private fun currentState(): State {
        if (geoipFile.exists() && geositeFile.exists()) {
            // 1.0.1.2 (#114): the FILES are what the core uses — a
            // missing meta file (old files, a restored backup) used to
            // report "Missing" while geo routing was live. Report Ready
            // with an unknown version instead.
            val version = readMeta()?.version
                ?: context.getString(R.string.geo_version_unknown)
            return State.Ready(version)
        }
        return State.Missing
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
        // 3.2.11: the Psiphon list rides along — the Aether core needs
        // it unpacked before the first Psiphon connect, and isReady()
        // is asked on every connect.
        ensurePsiphonList()
        // 3.2.20: no bundled files any more — a fresh install answers
        // false until the user downloads geo from Settings, and the
        // core runs its minimal config until then.
        return ensurePresent()
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
     * Every built-in source pins a checksum (issue #95); a source
     * whose checksum cannot be fetched fails instead of installing
     * unverified bytes. Throws when every source fails.
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
        // Verify the checksum. Every built-in source pins one (issue
        // #95); when a checksum was expected but could not be fetched,
        // the download fails instead of installing unverified bytes.
        // A null shaUrl means a user-supplied URL with no checksum to
        // check (custom asset URLs) — those are format-validated by
        // the caller instead.
        val expected = fetchChecksum(shaUrl)
        if (shaUrl != null && expected == null) {
            throw java.io.IOException("checksum unavailable")
        }
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
        /** The URL each asset downloads from unless the user set another (3.2.11, PattNG UserAssetUrlActivity). */
        const val DEFAULT_GEOIP_URL = "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat"
        const val DEFAULT_GEOSITE_URL = "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat"

        /**
         * Per file: (download URL, checksum URL or null). First entry is
         * the jsDelivr mirror of the v2fly release (Loyalsoldier's
         * release branch, same bytes), second is GitHub itself.
         * - geoip.dat: https://github.com/v2fly/geoip/releases/latest/download/geoip.dat
         * - geosite.dat: https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat
         *   (dlc.dat IS the geosite.dat file, renamed on save)
         */
        private val GEOIP_SOURCES = listOf(
            // 1.0.1.2 (#95): jsDelivr publishes the .sha256sum files
            // too (verified live) — the primary mirror is now verified
            // like the GitHub source.
            "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat" to
                "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat.sha256sum",
            "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat" to
                "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat.sha256sum",
        )
        private val GEOSITE_SOURCES = listOf(
            "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat" to
                "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat.sha256sum",
            "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat" to
                "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat.sha256sum",
        )
    }
}
