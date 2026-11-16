package com.amyrmo.damavand.lite

import android.content.Context
import android.content.Intent

/**
 * Start extras for a selected chain ("chain:<id>") or Auto entry
 * ("auto:all" / "auto:manual" / "auto:sub:<url>"), built the way the
 * ViewModel's connectToChain / connectToAuto build them. The quick
 * settings tile, the shortcuts / Tasker and boot auto-start use it: they
 * used to look the selected id up among profiles only, so a selected
 * chain or Auto entry fell back to a single "fastest" server (or, at
 * boot, to nothing).
 */
object SelectedEntry {

    /** True when [selectedId] names a chain or an Auto entry, not a profile. */
    fun isGroup(selectedId: String?): Boolean =
        selectedId != null && (selectedId.startsWith("chain:") || AutoSelectIds.isAuto(selectedId))

    /**
     * An Intent carrying only the extras that start [selectedId]
     * (copy them with Intent.putExtras), or null when it is not a
     * chain / Auto entry or no longer resolves to something connectable
     * (a deleted chain, a chain pruned below two members, an empty group).
     */
    fun startExtras(context: Context, selectedId: String?): Intent? {
        if (selectedId == null || !isGroup(selectedId)) return null
        return try {
            val repo = ProfileRepository(context)
            val profiles = repo.loadProfiles()
            if (selectedId.startsWith("chain:")) {
                val chain = repo.loadChains().firstOrNull { "chain:${it.id}" == selectedId }
                    ?: return null
                val links = chain.memberIds.mapNotNull { id ->
                    profiles.firstOrNull { it.id == id }?.link
                }
                if (links.size < 2) return null // not a chain any more
                return Intent().putStringArrayListExtra(LiteVpnService.EXTRA_LINKS, ArrayList(links))
            }
            val groupKey = AutoSelectIds.groupKeyOf(selectedId)
            // Only profiles that build as an autoselect member: an Aether
            // or custom-JSON profile has no single outbound.
            val members = (if (groupKey == "__all__") profiles else profiles.filter { it.subscriptionUrl == groupKey })
                .filter { !it.isAether && it.proto != "custom" }
            when {
                members.isEmpty() -> null
                // One member is just a direct connect (no group to measure).
                members.size == 1 -> Intent().putExtra(LiteVpnService.EXTRA_LINK, members[0].link)
                else -> {
                    val label = when (groupKey) {
                        "__all__" -> context.getString(R.string.servers_group_all)
                        "" -> context.getString(R.string.servers_group_manual)
                        else -> repo.loadSubscriptions().firstOrNull { it.url == groupKey }?.displayName
                            ?: groupKey.substringAfter("://").substringBefore("/")
                    }
                    Intent()
                        .putStringArrayListExtra(LiteVpnService.EXTRA_LINKS, ArrayList(members.map { it.link }))
                        .putExtra(LiteVpnService.EXTRA_AUTO_SELECT, true)
                        .putExtra(LiteVpnService.EXTRA_AUTO_ID, selectedId)
                        .putExtra(LiteVpnService.EXTRA_NAME, context.getString(R.string.auto_card_title, label))
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
