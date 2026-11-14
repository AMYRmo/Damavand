package com.amyrmo.damavand.lite

import android.content.Context
import android.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Profiles + subscriptions, stored as plain JSON files.
 *
 * Subscriptions are NEVER shown as a browsable list (Khazar rule carried
 * over): they are imported, their profiles appear in the Servers tab, and
 * that is all the user ever sees of them.
 */
class ProfileRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val profilesFile: File get() = File(context.filesDir, "profiles.json")
    private val subscriptionsFile: File get() = File(context.filesDir, "subscriptions.json")

    // ---- profiles ----

    fun loadProfiles(): List<Profile> {
        return try {
            if (!profilesFile.exists()) return emptyList()
            json.decodeFromString<List<Profile>>(profilesFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfiles(profiles: List<Profile>) {
        try {
            profilesFile.writeText(json.encodeToString(profiles))
        } catch (_: Exception) {
        }
    }

    fun selectedProfile(selectedId: String?): Profile? {
        val profiles = loadProfiles()
        if (profiles.isEmpty()) return null
        return profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    }

    fun updateProfile(updated: Profile) {
        val profiles = loadProfiles().map { if (it.id == updated.id) updated else it }
        saveProfiles(profiles)
    }

    fun updatePing(profileId: String, pingMs: Long) {
        val now = System.currentTimeMillis()
        val profiles = loadProfiles().map {
            if (it.id == profileId) it.copy(lastPingMs = pingMs, lastTestedAt = now) else it
        }
        saveProfiles(profiles)
    }

    // ---- subscriptions ----

    fun loadSubscriptions(): List<String> {
        return try {
            if (!subscriptionsFile.exists()) return emptyList()
            json.decodeFromString<List<String>>(subscriptionsFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSubscriptions(urls: List<String>) {
        try {
            subscriptionsFile.writeText(json.encodeToString(urls))
        } catch (_: Exception) {
        }
    }

    /**
     * Fetch one subscription URL and return the profiles it contains.
     * The body may be plain text (one link per line) or base64-encoded.
     * Returns an empty list on any failure (caller reports it).
     */
    fun fetchSubscription(url: String): List<Profile> {
        val body = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "DamavandLite/2.0.0")
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return emptyList()
            conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
        } catch (_: Exception) {
            return emptyList()
        }
        val text = decodeSubscriptionBody(body)
        return parseLinks(text, url)
    }

    private fun decodeSubscriptionBody(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ""
        // Plain text already contains share links.
        if (trimmed.contains("://")) return trimmed
        // Otherwise try base64 (standard, then URL-safe).
        for (flags in listOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            try {
                val decoded = String(Base64.decode(trimmed, flags), Charsets.UTF_8)
                if (decoded.contains("://")) return decoded
            } catch (_: Exception) {
            }
        }
        return trimmed
    }

    private fun parseLinks(text: String, subscriptionUrl: String): List<Profile> {
        val out = ArrayList<Profile>()
        val seen = HashSet<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || !line.contains("://")) continue
            if (!seen.add(line)) continue
            val parsed = XrayConfig.parse(line) ?: continue
            val remark = line.substringAfter("#", "").let {
                try {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } catch (_: Exception) {
                    it
                }
            }.trim()
            val name = remark.ifEmpty { "${parsed.addr}:${parsed.port}" }
            out.add(
                Profile(
                    id = Profile.idFor(line),
                    link = line,
                    name = name,
                    proto = parsed.proto,
                    address = parsed.addr,
                    port = parsed.port,
                    subscriptionUrl = subscriptionUrl,
                )
            )
        }
        return out
    }

    /**
     * Add a subscription: fetch it, merge its profiles (dedupe by link),
     * remember the URL. Returns the number of new profiles added, or -1
     * when the fetch failed.
     */
    fun addSubscription(url: String): Int {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return -1
        val fetched = fetchSubscription(trimmed)
        if (fetched.isEmpty()) return -1
        val existing = loadProfiles()
        val existingLinks = existing.map { it.link }.toHashSet()
        val fresh = fetched.filter { it.link !in existingLinks }
        saveProfiles(existing + fresh)
        val subs = loadSubscriptions()
        if (trimmed !in subs) saveSubscriptions(subs + trimmed)
        return fresh.size
    }

    /** Re-fetch every stored subscription and merge (dedupe by link). */
    fun refreshAll(): Int {
        var added = 0
        val existing = loadProfiles()
        val existingLinks = existing.map { it.link }.toHashSet()
        val merged = existing.toMutableList()
        for (url in loadSubscriptions()) {
            val fetched = fetchSubscription(url)
            for (p in fetched) {
                if (p.link !in existingLinks) {
                    merged.add(p)
                    existingLinks.add(p.link)
                    added++
                }
            }
        }
        if (added > 0) saveProfiles(merged)
        return added
    }
}
