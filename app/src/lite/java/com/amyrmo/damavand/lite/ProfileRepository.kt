package com.amyrmo.damavand.lite

import android.content.Context
import android.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
    private val chainsFile: File get() = File(context.filesDir, "chains.json")
    private val deletedLinksFile: File get() = File(context.filesDir, "deleted-links.json")

    /**
     * 3.1.1 — the ping-wipe race: test-all ran one updatePing per
     * profile IN PARALLEL, each a load → modify → save on profiles.json
     * with a plain writeText. A load landing mid-write saw a truncated
     * file, decoded to [], and saved [] — every config gone. So:
     * (a) every read-modify-write runs under [fileLock], and
     * (b) every write is atomic (temp file + rename), so a reader
     * never sees a torn file. synchronized() is reentrant, so nested
     * calls (deleteProfile → markDeleted → pruneChains) are safe.
     */
    private val fileLock = Any()

    /** Write [text] to [file] atomically via temp + rename. */
    private fun atomicWrite(file: File, text: String) {
        try {
            val tmp = File(file.parent, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) file.writeText(text)
        } catch (_: Exception) {
            try {
                file.writeText(text)
            } catch (_: Exception) {
            }
        }
    }

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
            atomicWrite(profilesFile, json.encodeToString(profiles))
        } catch (_: Exception) {
        }
    }

    /** Re-order the stored profiles in one locked load → sort → save. */
    fun sortProfiles(comparator: Comparator<Profile>) {
        synchronized(fileLock) {
            saveProfiles(loadProfiles().sortedWith(comparator))
        }
    }

    /** Append [profile] in one locked load → add → save. */
    fun addProfile(profile: Profile) {
        synchronized(fileLock) {
            saveProfiles(loadProfiles() + profile)
        }
    }

    fun selectedProfile(selectedId: String?): Profile? {
        val profiles = loadProfiles()
        if (profiles.isEmpty()) return null
        return profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    }

    fun updateProfile(updated: Profile) {
        synchronized(fileLock) {
            val profiles = loadProfiles().map { if (it.id == updated.id) updated else it }
            saveProfiles(profiles)
        }
    }

    fun updatePing(profileId: String, pingMs: Long) {
        synchronized(fileLock) {
            val now = System.currentTimeMillis()
            val profiles = loadProfiles().map {
                if (it.id == profileId) it.copy(lastPingMs = pingMs, lastTestedAt = now) else it
            }
            saveProfiles(profiles)
        }
    }

    /**
     * 3.1.1 — test-all's batched ping save: ONE load, apply every
     * result, ONE save. No per-profile file rewrite, no race at all.
     */
    fun updatePings(pings: Map<String, Long>) {
        if (pings.isEmpty()) return
        synchronized(fileLock) {
            val now = System.currentTimeMillis()
            val profiles = loadProfiles().map {
                val ping = pings[it.id]
                if (ping != null) it.copy(lastPingMs = ping, lastTestedAt = now) else it
            }
            saveProfiles(profiles)
        }
    }

    /**
     * Delete one profile for good: it leaves the list, its link goes on
     * the deleted-links blocklist (so a subscription refresh does NOT
     * resurrect it), and every chain loses it as a member.
     */
    fun deleteProfile(id: String) {
        synchronized(fileLock) {
            val profiles = loadProfiles()
            val profile = profiles.firstOrNull { it.id == id } ?: return
            saveProfiles(profiles.filter { it.id != id })
            markDeleted(profile.link)
            pruneChains(id)
        }
    }

    /** A copy of [profile] with a fresh id and a localized «copy» suffix in its name. */
    fun duplicateProfile(profile: Profile): Profile {
        synchronized(fileLock) {
            val copy = profile.copy(
                id = Profile.idFor(profile.link) + "-c" + System.currentTimeMillis().toString(16),
                name = profile.name + context.getString(R.string.profile_copy_suffix),
            )
            saveProfiles(loadProfiles() + copy)
            return copy
        }
    }

    /** Rename a profile (its link is untouched). */
    fun renameProfile(id: String, name: String) {
        synchronized(fileLock) {
            val profiles = loadProfiles().map {
                if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it
            }
            saveProfiles(profiles)
        }
    }

    /**
     * Replace a profile's link: re-parse the new link and update the
     * protocol/address/port from it. Returns false when the new link
     * does not parse.
     */
    fun updateProfileLink(id: String, newLink: String): Boolean {
        val parsed = XrayConfig.parse(newLink.trim()) ?: return false
        val remark = newLink.trim().substringAfter("#", "").let {
            try {
                java.net.URLDecoder.decode(it, "UTF-8")
            } catch (_: Exception) {
                it
            }
        }.trim()
        synchronized(fileLock) {
            val profiles = loadProfiles().map {
                if (it.id == id) {
                    it.copy(
                        link = newLink.trim(),
                        proto = parsed.proto,
                        address = parsed.addr,
                        port = parsed.port,
                        name = remark.ifEmpty { it.name },
                    )
                } else it
            }
            saveProfiles(profiles)
        }
        return true
    }

    /** Move a profile into another subscription group ("" = دستی). */
    fun moveProfile(id: String, subscriptionUrl: String) {
        synchronized(fileLock) {
            val profiles = loadProfiles().map {
                if (it.id == id) it.copy(subscriptionUrl = subscriptionUrl) else it
            }
            saveProfiles(profiles)
        }
    }

    // ---- deleted-links blocklist (3.0.0, PattNG delete) ----

    private fun loadDeletedLinkIds(): Set<String> {
        return try {
            if (!deletedLinksFile.exists()) emptySet()
            else json.decodeFromString<Set<String>>(deletedLinksFile.readText())
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun markDeleted(link: String) {
        try {
            val ids = loadDeletedLinkIds().toMutableSet()
            ids.add(Profile.idFor(link))
            atomicWrite(deletedLinksFile, json.encodeToString(ids.toList()))
        } catch (_: Exception) {
        }
    }

    /** A re-pasted single link is explicit intent: lift the block. */
    private fun unmarkDeleted(link: String) {
        try {
            val ids = loadDeletedLinkIds().toMutableSet()
            if (ids.remove(Profile.idFor(link))) {
                atomicWrite(deletedLinksFile, json.encodeToString(ids.toList()))
            }
        } catch (_: Exception) {
        }
    }

    // ---- chains (3.0.0, PattNG PROXYCHAIN) ----

    fun loadChains(): List<Chain> {
        return try {
            if (!chainsFile.exists()) emptyList()
            else json.decodeFromString<List<Chain>>(chainsFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveChains(chains: List<Chain>) {
        try {
            atomicWrite(chainsFile, json.encodeToString(chains))
        } catch (_: Exception) {
        }
    }

    /** Create a chain from the picked member ids (entry first, exit last). */
    fun addChain(memberIds: List<String>): Chain {
        synchronized(fileLock) {
            val chains = loadChains()
            var n = 1
            while (chains.any { it.name == context.getString(R.string.chain_default_name, n) }) n++
            val chain = Chain(
                id = "chain-" + System.currentTimeMillis().toString(16),
                name = context.getString(R.string.chain_default_name, n),
                memberIds = memberIds
            )
            saveChains(chains + chain)
            return chain
        }
    }

    fun deleteChain(id: String) {
        synchronized(fileLock) {
            saveChains(loadChains().filter { it.id != id })
        }
    }

    /**
     * Drop [profileId] from every chain. A chain left with fewer than
     * two members is deleted outright — a one-member chain is just the
     * profile itself (PattNG treats a one-member chain as NORMAL).
     */
    fun pruneChains(profileId: String) {
        synchronized(fileLock) {
            val pruned = loadChains().mapNotNull { chain ->
                val members = chain.memberIds.filter { it != profileId }
                if (members.size < 2) null else chain.copy(memberIds = members)
            }
            saveChains(pruned)
        }
    }

    // ---- subscriptions (3.1.0: full PattNG management) ----

    /**
     * Result of a subscription fetch, so the UI can say WHY it failed
     * instead of one generic error (2.0.1 — on-device 2.0.0 gave no
     * usable reason when a GitHub-hosted sub would not load).
     */
    sealed interface FetchResult {
        /** [profiles] are the newly parsed ones; [skippedUnsupported] counts
         *  links whose protocol Lite does not speak (tuic, …). */
        data class Ok(
            val profiles: List<Profile>,
            val skippedUnsupported: Int,
            val userInfo: SubUserInfo? = null,
        ) : FetchResult
        /** Nothing could be downloaded from any candidate URL. */
        data object NetworkError : FetchResult
        /** A body downloaded, but it held no usable link at all. */
        data class NoUsableLinks(val skippedUnsupported: Int) : FetchResult
        /** A pasted config link (not a subscription URL) that Lite cannot parse. */
        data object SingleLinkInvalid : FetchResult
    }

    /**
     * Stored subscriptions. The file used to hold a plain list of URL
     * strings (3.0.0); an old file migrates to [Subscription] objects
     * on first read.
     */
    fun loadSubscriptions(): List<Subscription> {
        return try {
            if (!subscriptionsFile.exists()) return emptyList()
            val text = subscriptionsFile.readText()
            try {
                json.decodeFromString<List<Subscription>>(text)
            } catch (_: Exception) {
                json.decodeFromString<List<String>>(text).map { Subscription(url = it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSubscriptions(subs: List<Subscription>) {
        try {
            atomicWrite(subscriptionsFile, json.encodeToString(subs))
        } catch (_: Exception) {
        }
    }

    fun renameSubscription(url: String, name: String) {
        synchronized(fileLock) {
            saveSubscriptions(loadSubscriptions().map {
                if (it.url == url) it.copy(name = name.trim()) else it
            })
        }
    }

    fun setSubscriptionAutoUpdate(url: String, autoUpdate: Boolean) {
        synchronized(fileLock) {
            saveSubscriptions(loadSubscriptions().map {
                if (it.url == url) it.copy(autoUpdate = autoUpdate) else it
            })
        }
    }

    /**
     * Delete a subscription and every profile that came from it. The
     * profiles are NOT blocklisted — the subscription itself is gone,
     * so a refresh can never resurrect them.
     */
    fun deleteSubscription(url: String) {
        synchronized(fileLock) {
            saveSubscriptions(loadSubscriptions().filter { it.url != url })
            val remaining = loadProfiles().filter { it.subscriptionUrl != url }
            saveProfiles(remaining)
            // Drop any chain member that no longer exists; a chain left
            // with fewer than two members is deleted outright.
            val alive = remaining.map { it.id }.toSet()
            val pruned = loadChains().mapNotNull { chain ->
                val members = chain.memberIds.filter { it in alive }
                if (members.size < 2) null else chain.copy(memberIds = members)
            }
            saveChains(pruned)
        }
    }

    /** Re-fetch ONE subscription and merge (dedupe by link). */
    fun refreshOne(url: String): FetchResult {
        // Network first, OUTSIDE the lock — the merge below is the
        // only part that touches files (3.1.1).
        return when (val fetched = fetchSubscription(url)) {
            is FetchResult.Ok -> {
                synchronized(fileLock) {
                    val existing = loadProfiles()
                    val existingLinks = existing.map { it.link }.toHashSet()
                    val blocked = loadDeletedLinkIds()
                    val fresh = fetched.profiles.filter {
                        it.link !in existingLinks && it.id !in blocked
                    }
                    saveProfiles(existing + fresh)
                    val info = fetched.userInfo
                    saveSubscriptions(loadSubscriptions().map {
                        if (it.url == url) it.copy(
                            lastUpdated = System.currentTimeMillis(),
                            upload = info?.upload ?: it.upload,
                            download = info?.download ?: it.download,
                            total = info?.total ?: it.total,
                            expire = info?.expire ?: it.expire,
                            websiteUrl = info?.websiteUrl?.ifBlank { it.websiteUrl } ?: it.websiteUrl,
                            supportUrl = info?.supportUrl?.ifBlank { it.supportUrl } ?: it.supportUrl,
                        ) else it
                    })
                    FetchResult.Ok(fresh, fetched.skippedUnsupported, info)
                }
            }
            else -> fetched
        }
    }

    /**
     * GitHub file-page URLs pasted from a browser do not serve the raw
     * file — normalize them to raw.githubusercontent.com first:
     * - github.com/u/r/blob/b/p → raw.githubusercontent.com/u/r/b/p
     * - github.com/u/r/raw/refs/heads/b/p → raw.githubusercontent.com/u/r/b/p
     * - raw.githubusercontent.com/u/r/refs/heads/b/p → raw.githubusercontent.com/u/r/b/p
     */
    fun normalizeSubUrl(url: String): String {
        val u = url.trim()
        val ghBlob = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$").matchEntire(u)
        if (ghBlob != null) {
            val (user, repo, branch, path) = ghBlob.destructured
            return "https://raw.githubusercontent.com/$user/$repo/$branch/$path"
        }
        val ghRaw = Regex("^https://github\\.com/([^/]+)/([^/]+)/raw/(?:refs/heads/)?([^/]+)/(.+)$").matchEntire(u)
        if (ghRaw != null) {
            val (user, repo, branch, path) = ghRaw.destructured
            return "https://raw.githubusercontent.com/$user/$repo/$branch/$path"
        }
        val rawRef = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/refs/heads/([^/]+)/(.+)$").matchEntire(u)
        if (rawRef != null) {
            val (user, repo, branch, path) = rawRef.destructured
            return "https://raw.githubusercontent.com/$user/$repo/$branch/$path"
        }
        return u
    }

    /**
     * jsDelivr mirror for a raw.githubusercontent.com URL — the same
     * file from a CDN that is reachable when GitHub itself is not
     * (seen on-device: github.com would not load without a VPN).
     * Returns null for non-GitHub URLs.
     */
    fun jsDelivrMirror(rawUrl: String): String? {
        val m = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$").matchEntire(rawUrl)
            ?: return null
        val (user, repo, branch, path) = m.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$branch/$path"
    }

    /**
     * Plain download of one URL; null body on any failure. Uses [HttpFetch],
     * which tries the default route first and then every non-VPN network
     * underneath it — so a subscription loads whether the VPN is on or
     * off, even when one of the two paths is broken (2.0.2). Also
     * returns the parsed `subscription-userinfo` header when the
     * panel sends one (3.2.0, subscription card).
     */
    private fun fetchBody(url: String): Pair<String?, SubUserInfo?> {
        val conn = HttpFetch.openConnection(context, url, 15_000, 30_000, "DamavandLite/2.0.2")
            ?: return null to null
        return try {
            val userInfo = parseUserInfo(conn.getHeaderField("subscription-userinfo"))
            conn.inputStream.bufferedReader().readText() to userInfo
        } catch (_: Exception) {
            null to null
        } finally {
            conn.disconnect()
        }
    }

    /** Parse `upload=…; download=…; total=…; expire=…` (bytes, epoch s),
     *  plus `profile-web-page-url=…` / `support-url=…` (3.2.1). */
    private fun parseUserInfo(header: String?): SubUserInfo? {
        if (header.isNullOrBlank()) return null
        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = 0L
        var websiteUrl = ""
        var supportUrl = ""
        for (part in header.split(';')) {
            val kv = part.trim().split('=', limit = 2)
            if (kv.size != 2) continue
            val key = kv[0].trim().lowercase()
            val raw = kv[1].trim()
            when (key) {
                "profile-web-page-url" -> websiteUrl = raw
                "support-url" -> supportUrl = raw
                else -> {
                    val value = raw.toLongOrNull() ?: continue
                    when (key) {
                        "upload" -> upload = value
                        "download" -> download = value
                        "total" -> total = value
                        "expire" -> expire = value
                    }
                }
            }
        }
        return SubUserInfo(upload, download, total, expire, websiteUrl, supportUrl)
    }

    /**
     * Fetch one subscription URL and return the profiles it contains.
     * The body may be plain text (one link per line) or base64-encoded.
     * Tries the URL as given (normalized), then its jsDelivr mirror.
     */
    fun fetchSubscription(url: String): FetchResult {
        val normalized = normalizeSubUrl(url)
        val candidates = listOfNotNull(normalized, jsDelivrMirror(normalized)).distinct()
        var anyBody = false
        var skipped = 0
        for (candidate in candidates) {
            val (body, userInfo) = fetchBody(candidate)
            if (body == null) continue
            anyBody = true
            val text = decodeSubscriptionBody(body)
            val (profiles, skippedHere) = parseLinks(text, url)
            skipped = maxOf(skipped, skippedHere)
            if (profiles.isNotEmpty()) return FetchResult.Ok(profiles, skipped, userInfo)
        }
        return if (anyBody) FetchResult.NoUsableLinks(skipped) else FetchResult.NetworkError
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

    private fun parseLinks(text: String, subscriptionUrl: String): Pair<List<Profile>, Int> {
        val out = ArrayList<Profile>()
        val seen = HashSet<String>()
        var skippedUnsupported = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || !line.contains("://")) continue
            if (!seen.add(line)) continue
            val parsed = XrayConfig.parse(line)
            if (parsed == null) {
                // Count only links whose scheme Lite does not speak at
                // all; malformed lines of a supported scheme stay silent.
                val scheme = line.substringBefore("://").lowercase()
                if (scheme !in setOf(
                        "vless", "vmess", "trojan", "ss",
                        "socks", "http", "hysteria2", "wg", "custom"
                    )
                ) skippedUnsupported++
                continue
            }
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
        return out to skippedUnsupported
    }

    /**
     * Add one pasted config link — or several, one per line — directly,
     * without any subscription (2.0.2, asked on-device 2026-11-15:
     * «به جز ساب کانفیگ تکی هم قبول کنه»). Lines are parsed with the
     * same parser as subscriptions; unparseable lines are skipped.
     * Returns [FetchResult.Ok] with the newly added profiles (possibly
     * empty when they were already imported), or
     * [FetchResult.SingleLinkInvalid] when nothing at all parsed.
     */
    fun addSingleLinks(text: String): FetchResult {
        val (profiles, skipped) = parseLinks(text, "")
        if (profiles.isEmpty()) return FetchResult.SingleLinkInvalid
        synchronized(fileLock) {
            val existing = loadProfiles()
            val existingLinks = existing.map { it.link }.toHashSet()
            val fresh = profiles.filter { it.link !in existingLinks }
            fresh.forEach { unmarkDeleted(it.link) }
            saveProfiles(existing + fresh)
            return FetchResult.Ok(fresh, skipped)
        }
    }

    /**
     * Add a subscription: fetch it, merge its profiles (dedupe by link),
     * remember the URL. Returns the fetch result; on [FetchResult.Ok]
     * the profiles are already merged (the list holds the newly added
     * ones, which may be empty when everything was already imported).
     * Anything that is not an http(s) URL is treated as pasted config
     * link(s) instead (2.0.2).
     */
    fun addSubscription(url: String): FetchResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return FetchResult.NetworkError
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return addSingleLinks(trimmed)
        }
        // Network first, OUTSIDE the lock — the merge below is the
        // only part that touches files (3.1.1).
        return when (val fetched = fetchSubscription(trimmed)) {
            is FetchResult.Ok -> {
                synchronized(fileLock) {
                    val existing = loadProfiles()
                    val existingLinks = existing.map { it.link }.toHashSet()
                    val blocked = loadDeletedLinkIds()
                    val fresh = fetched.profiles.filter {
                        it.link !in existingLinks && it.id !in blocked
                    }
                    saveProfiles(existing + fresh)
                    val subs = loadSubscriptions()
                    val info = fetched.userInfo
                    if (subs.none { it.url == trimmed }) {
                        val name = trimmed.substringAfter("://").substringBefore("/").substringBefore("?")
                        saveSubscriptions(
                            subs + Subscription(
                                url = trimmed,
                                name = name,
                                lastUpdated = System.currentTimeMillis(),
                                upload = info?.upload ?: 0,
                                download = info?.download ?: 0,
                                total = info?.total ?: 0,
                                expire = info?.expire ?: 0,
                                websiteUrl = info?.websiteUrl ?: "",
                                supportUrl = info?.supportUrl ?: "",
                            )
                        )
                    } else if (info != null) {
                        saveSubscriptions(subs.map {
                            if (it.url == trimmed) it.copy(
                                lastUpdated = System.currentTimeMillis(),
                                upload = info.upload,
                                download = info.download,
                                total = info.total,
                                expire = info.expire,
                                websiteUrl = info.websiteUrl.ifBlank { it.websiteUrl },
                                supportUrl = info.supportUrl.ifBlank { it.supportUrl },
                            ) else it
                        })
                    }
                    FetchResult.Ok(fresh, fetched.skippedUnsupported, info)
                }
            }
            else -> fetched
        }
    }

    /** Re-fetch every stored subscription (that has auto-update on) and merge (dedupe by link). */
    fun refreshAll(): Int {
        // Fetch everything first (network, OUTSIDE the lock), then
        // merge once under the lock (3.1.1).
        val subs = loadSubscriptions()
        val fetched = subs.filter { it.autoUpdate }.map { it to fetchSubscription(it.url) }
        synchronized(fileLock) {
            var added = 0
            val existing = loadProfiles()
            val existingLinks = existing.map { it.link }.toHashSet()
            val blocked = loadDeletedLinkIds()
            val merged = existing.toMutableList()
            for ((_, result) in fetched) {
                if (result !is FetchResult.Ok) continue
                for (p in result.profiles) {
                    if (p.link !in existingLinks && p.id !in blocked) {
                        merged.add(p)
                        existingLinks.add(p.link)
                        added++
                    }
                }
            }
            if (added > 0) saveProfiles(merged)
            if (subs.isNotEmpty()) {
                val infoByUrl = fetched.mapNotNull { (sub, result) ->
                    (result as? FetchResult.Ok)?.userInfo?.let { sub.url to it }
                }.toMap()
                saveSubscriptions(subs.map { sub ->
                    val info = infoByUrl[sub.url]
                    sub.copy(
                        lastUpdated = System.currentTimeMillis(),
                        upload = info?.upload ?: sub.upload,
                        download = info?.download ?: sub.download,
                        total = info?.total ?: sub.total,
                        expire = info?.expire ?: sub.expire,
                        websiteUrl = info?.websiteUrl?.ifBlank { sub.websiteUrl } ?: sub.websiteUrl,
                        supportUrl = info?.supportUrl?.ifBlank { sub.supportUrl } ?: sub.supportUrl,
                    )
                })
            }
            return added
        }
    }
}
