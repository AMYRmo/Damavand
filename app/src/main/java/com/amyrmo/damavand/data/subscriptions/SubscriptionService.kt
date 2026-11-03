package com.amyrmo.damavand.data.subscriptions

import com.amyrmo.damavand.data.profiles.ProfileFactory
import com.amyrmo.damavand.data.profiles.ProfileRepository
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.Subscription
import com.amyrmo.damavand.model.SubscriptionHeaders
import com.amyrmo.damavand.model.SubscriptionParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Subscription fetching/refreshing (architecture §3.5) — the piece the
 * repository doc leaves to the UI phase. A refresh downloads the body,
 * parses `subscription-userinfo` / `profile-title` headers into the
 * stored snapshot, rebuilds the member list through the
 * [ProfileFactory] cascade, and reconciles ids by payload identity so
 * existing members (and chains that reference them) survive a refresh.
 */
class SubscriptionService(
    private val profiles: ProfileRepository,
    private val subscriptions: SubscriptionRepository,
) {

    /** Create a subscription for [url] and fetch it once. */
    suspend fun add(url: String, name: String? = null): Result<Subscription> {
        val trimmed = url.trim()
        val host = runCatching { URI(trimmed).host }.getOrNull()
        val sub = Subscription(
            id = UUID.randomUUID().toString(),
            name = name?.takeIf { it.isNotBlank() } ?: host ?: trimmed,
            url = trimmed,
        )
        subscriptions.upsert(sub)
        return refresh(sub)
    }

    /** Re-download [subscription] and reconcile its members + snapshot. */
    suspend fun refresh(subscription: Subscription): Result<Subscription> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (headers, body) = fetch(subscription)
                val meta = SubscriptionHeaders.parse(headers)
                val members = buildMembers(subscription, body)
                check(members.isNotEmpty()) { "the response contained no supported servers" }
                reconcile(subscription, members)
                val updated = subscription.copy(
                    name = subscription.name.ifBlank { meta.title ?: subscription.name },
                    lastUpdated = System.currentTimeMillis(),
                    serverCount = members.size,
                    upload = meta.upload ?: subscription.upload,
                    download = meta.download ?: subscription.download,
                    total = meta.total ?: subscription.total,
                    expireAt = meta.expireAt ?: subscription.expireAt,
                    title = meta.title ?: subscription.title,
                    updateIntervalHours = meta.updateIntervalHours ?: subscription.updateIntervalHours,
                    supportUrl = meta.supportUrl ?: subscription.supportUrl,
                    webPageUrl = meta.webPageUrl ?: subscription.webPageUrl,
                )
                subscriptions.upsert(updated)
                updated
            }
        }

    private fun fetch(subscription: Subscription): Pair<Map<String, String>, String> {
        val conn = URL(subscription.url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.requestMethod = "GET"
        conn.setRequestProperty(
            "User-Agent",
            subscription.userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT,
        )
        try {
            val code = conn.responseCode
            check(code in 200..299) { "HTTP $code" }
            val headers = conn.headerFields.entries
                .filter { it.key != null }
                .associate { it.key to (it.value.firstOrNull().orEmpty()) }
            val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            return headers to body
        } finally {
            conn.disconnect()
        }
    }

    private fun buildMembers(subscription: Subscription, body: String): List<Profile> {
        // A Clash subscription returns one YAML document, not a link list.
        if (MihomoProfile.looksLikeMihomo(body)) {
            val existing = profiles.profiles.value
                .firstOrNull { it.subscriptionId == subscription.id && it is MihomoProfile }
            return listOf(
                MihomoProfile(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = subscription.name,
                    subscriptionId = subscription.id,
                    yaml = body.trim(),
                ),
            )
        }
        return SubscriptionParser.extractLinks(body).mapNotNull { link ->
            ProfileFactory.fromLink(link, UUID.randomUUID().toString(), subscription.id)
        }
    }

    private fun reconcile(subscription: Subscription, members: List<Profile>) {
        val existing = profiles.profiles.value.filter { it.subscriptionId == subscription.id }
        val existingByKey = existing.associateBy { ProfileFactory.identityKey(it) }
        val keptIds = mutableSetOf<String>()
        for (member in members) {
            val prior = existingByKey[ProfileFactory.identityKey(member)]
            val resolved = if (prior != null && prior.id != member.id) {
                withId(member, prior.id)
            } else {
                member
            }
            keptIds += resolved.id
            profiles.upsert(resolved)
        }
        for (stale in existing) {
            if (stale.id !in keptIds) profiles.remove(stale.id)
        }
    }

    private fun withId(profile: Profile, id: String): Profile = when (profile) {
        is com.amyrmo.damavand.model.XrayProfile -> profile.copy(id = id)
        is com.amyrmo.damavand.model.SingBoxProfile -> profile.copy(id = id)
        is com.amyrmo.damavand.model.PsiphonProfile -> profile.copy(id = id)
        is com.amyrmo.damavand.model.AetherProfile -> profile.copy(id = id)
        is MihomoProfile -> profile.copy(id = id)
        is com.amyrmo.damavand.model.WhitednsProfile -> profile.copy(id = id)
        is com.amyrmo.damavand.model.ChainProfile -> profile.copy(id = id)
        is com.amyrmo.damavand.model.AutoSelectProfile -> profile.copy(id = id)
    }

    companion object {
        private const val DEFAULT_USER_AGENT = "Damavand/2.0 (Android)"
    }
}
