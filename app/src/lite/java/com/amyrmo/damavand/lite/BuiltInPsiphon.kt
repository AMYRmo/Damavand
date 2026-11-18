package com.amyrmo.damavand.lite

import android.content.Context
import com.amyrmo.damavand.lite.aether.AetherPsiphon
import com.amyrmo.damavand.lite.aether.AetherSettings
import com.amyrmo.damavand.lite.aether.PsiphonServerList
import java.io.File
import java.util.Locale

/**
 * The built-in "Psiphon" subscription (1.0.1.5): one Psiphon-only
 * Aether profile per exit country, so the user picks a country in the
 * Servers tab and connects — no server of their own, no manual
 * Aether profile to build first.
 *
 * How it works: Psiphon needs no server address — the core dials
 * Psiphon's own public servers from the bundled list
 * (psiphon_servers.dat). A "Psiphon-only" Aether profile
 * ([AetherPsiphon.ONLY]) is exactly that: no WARP, the app dials
 * Psiphon itself, with `--psiphon-region <CC>` pinning the exit
 * country (or none for "Any country").
 *
 * The group is a plain [Subscription] whose URL is [URL]: a marker,
 * not an address — nothing is ever fetched from it. Its profiles
 * carry that URL as [Profile.subscriptionUrl], so the Servers tab
 * groups them under a "Psiphon" chip exactly like a real
 * subscription, with one row per country.
 *
 * Seeding is idempotent and respects deletion: profiles have stable
 * ids (`builtin-psiphon-<cc>`), so a later run only adds countries
 * the bundled list gained; and once the marker file exists, a
 * subscription the user deleted is never resurrected.
 */
object BuiltInPsiphon {

    /** The subscription URL of the built-in group — a marker, not an address. */
    const val URL = "builtin://psiphon"

    /** The name shown for the group in the Servers tab. */
    const val NAME = "Psiphon"

    fun isBuiltIn(url: String): Boolean = url == URL

    private const val MARKER = "builtin-psiphon-seeded"
    private const val ANY_ID = "builtin-psiphon-any"

    /**
     * Makes sure the built-in group exists: creates the subscription
     * and one profile per country on first run, adds only the
     * countries missing on later runs. A no-op when the user deleted
     * the group (the marker says it was seeded once already).
     * Runs on IO: it unpacks the bundled Psiphon list.
     */
    fun ensure(context: Context, repo: ProfileRepository, geo: GeoDownloader) {
        val hasSub = repo.loadSubscriptions().any { it.url == URL }
        val marker = File(context.filesDir, MARKER)
        if (!hasSub && marker.isFile) return
        runCatching { geo.ensurePsiphonList() }
        val regions = runCatching { PsiphonServerList.psiphonRegions(context) }
            .getOrDefault(emptyList())
        repo.addBuiltInProfiles(
            Subscription(url = URL, name = NAME, autoUpdate = false),
            buildProfiles(context, regions),
        )
        runCatching { marker.writeText("1") }
    }

    /**
     * The profiles of the group: "Any country" first, then one per
     * exit country the bundled list offers, sorted by localized
     * country name. An empty [regions] still seeds "Any country",
     * so the group is never empty.
     */
    fun buildProfiles(context: Context, regions: List<String>): List<Profile> {
        val list = ArrayList<Profile>()
        list += psiphonProfile(
            id = ANY_ID,
            name = context.getString(R.string.aether_psiphon_region_any),
            region = null,
        )
        val codes = regions.map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.length == 2 && it.all(Char::isLetter) }
            .distinct()
            .sortedBy { regionName(it) }
        for (code in codes) {
            list += psiphonProfile(
                id = "builtin-psiphon-${code.lowercase(Locale.ROOT)}",
                name = "${regionName(code)} ($code)",
                region = code,
            )
        }
        return list
    }

    private fun psiphonProfile(id: String, name: String, region: String?): Profile {
        val settings = AetherSettings(
            psiphon = AetherPsiphon.ONLY.type,
            psiphonRegion = region,
        )
        return Profile(
            id = id,
            link = "aether://$id",
            name = name,
            proto = "aether",
            address = "",
            port = 0,
            countryCode = region.orEmpty(),
            core = "Aether",
            aetherJson = AetherSettings.toJson(settings),
            subscriptionUrl = URL,
        )
    }

    /** The localized country name for an ISO code, the code itself when it has none. */
    fun regionName(code: String): String {
        val locale = Locale.getDefault()
        return runCatching {
            Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
    }
}
