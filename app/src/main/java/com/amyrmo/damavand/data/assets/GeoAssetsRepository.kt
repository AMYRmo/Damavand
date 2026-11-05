package com.amyrmo.damavand.data.assets

import com.amyrmo.damavand.platform.disconnectQuietly
import com.amyrmo.damavand.platform.platformHttpFailure
import android.content.Context
import com.amyrmo.damavand.core.AppLog as Log
import com.amyrmo.damavand.core.VpnManager
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.domain.model.GeoFilesSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class GeoAssetsRepository(private val context: Context) : GeoAssets {
    val assetDir: File
        get() = File(context.filesDir, "assets").apply { if (!exists()) mkdirs() }

    override fun allPresent(): Boolean = state().all { it.present }

    override fun state(): List<GeoAsset> = FILES.map { name ->
        val file = File(assetDir, name)
        GeoAsset(
            name = name,
            present = file.exists() && file.length() > 0,
            sizeBytes = if (file.exists()) file.length() else 0L,
            updatedAt = if (file.exists()) file.lastModified() else 0L,
        )
    }

    suspend fun seedBundled(appVersionCode: Long): Unit = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE)
        val seededVersion = prefs.getLong(KEY_SEED_VERSION, -1L)
        val appUpdated = seededVersion != appVersionCode
        var wrote = false

        for (name in BUNDLED) {
            val target = File(assetDir, name)
            val recorded = prefs.getLong(seedSizeKey(name), -1L)
            val missing = !target.exists() || target.length() <= 0L

            val ours = recorded > 0L && target.length() == recorded

            if (!missing && !(appUpdated && ours)) continue

            val copied = runCatching {
                val temp = File(assetDir, "$name.seed")
                context.assets.open(name).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (temp.length() <= 0L) {
                    temp.delete()
                    error("bundled asset $name is empty")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) error("could not move seeded $name into place")
                target.length()
            }.onFailure { Log.w(TAG, "seeding $name failed", it) }.getOrNull()

            if (copied != null) {
                prefs.edit().putLong(seedSizeKey(name), copied).apply()
                wrote = true
            }
        }

        if (appUpdated) prefs.edit().putLong(KEY_SEED_VERSION, appVersionCode).apply()
        if (wrote) Log.i(TAG, "seeded bundled geo assets into ${assetDir.absolutePath}")
    }

    override suspend fun downloadAll(
        source: GeoFilesSource,
    ): Boolean = withContext(Dispatchers.IO) {
        FILES.all { name ->
            val base = "https://github.com/${source.repo}/releases/latest/download"
            runCatching {
                // BPB port: pin every download to the SHA-256 published with
                // the same release; a mismatch rejects the update and keeps
                // the previously installed file.
                val expected = fetchSha256("$base/$name.sha256sum")
                download("$base/$name", File(assetDir, name), expected)
            }.isSuccess
        }
    }

    /** Downloads a `<file>.sha256sum` asset and returns its validated hash token. */
    private fun fetchSha256(url: String): String {
        val temp = File(assetDir, "sha256sum.tmp")
        try {
            download(url, temp, expectedSha256 = null)
            val token = temp.readText().trim()
                .split(Regex("\\s+")).firstOrNull().orEmpty().lowercase()
            require(token.matches(Regex("^[0-9a-f]{64}$"))) { "malformed sha256sum from $url" }
            return token
        } finally {
            temp.delete()
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun importFrom(stream: InputStream, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(assetDir, name)
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
            target.length() > 0
        }.getOrDefault(false)
    }

    override suspend fun importBytes(bytes: ByteArray, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(assetDir, name)
            target.outputStream().use { it.write(bytes) }
            target.length() > 0
        }.getOrDefault(false)
    }

    private fun download(url: String, target: File, expectedSha256: String?) {
        val socks = VpnManager.status.value.state
            .takeIf { it == ConnectionState.Connected }
            ?.let { VpnManager.activeSocksPort }
        if (socks != null) {
            val viaProxy = runCatching {
                download(url, target, Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(LOCALHOST, socks)), expectedSha256)
            }
            if (viaProxy.isSuccess) return
            Log.w(TAG, "proxied geo download failed, retrying direct", viaProxy.exceptionOrNull())
        }
        download(url, target, Proxy.NO_PROXY, expectedSha256)
    }

    private fun download(url: String, target: File, proxy: Proxy, expectedSha256: String?) {
        val connection = (URL(url).openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Damavand")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }

            val temp = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (temp.length() <= 0) throw IllegalStateException("empty download")
            if (expectedSha256 != null && !sha256Hex(temp).equals(expectedSha256, ignoreCase = true)) {
                temp.delete()
                throw IllegalStateException("SHA-256 mismatch for ${target.name}; update rejected")
            }
            if (target.exists()) target.delete()
            temp.renameTo(target)

            context.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE)
                .edit().remove(seedSizeKey(target.name)).apply()
        } catch (e: NullPointerException) {
            throw platformHttpFailure(e)
        } finally {
            connection.disconnectQuietly()
        }
    }

    companion object {
        private const val TAG = "GeoAssets"
        private const val LOCALHOST = "127.0.0.1"
        private const val SEED_PREFS = "zed_geo_seed"
        private const val KEY_SEED_VERSION = "seeded_version"
        private fun seedSizeKey(name: String) = "seed_size_$name"

        const val GEOIP = "geoip.dat"
        const val GEOSITE = "geosite.dat"

        val FILES = listOf(GEOIP, GEOSITE)

        val BUNDLED = listOf(GEOIP, GEOSITE)
    }
}
