package com.amyrmo.damavand.lite

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Resolve a stored selected id — a profile id, `chain:<id>` or
 * `auto:…` — into the same ACTION_START intent the app's Connect
 * button sends (3.2.16, #50). The QS tile, Tasker and the boot
 * receiver used to look the id up among profiles only, so a selected
 * chain connected ONE server directly (the notification showed the
 * single server — a privacy surprise) and a selected Auto entry did
 * nothing at boot. Returns null when the selection cannot be
 * resolved; callers keep their own fallback (tile/Tasker: fastest
 * five, boot: do nothing).
 *
 * The Auto branch mirrors LiteViewModel.connectToAuto exactly: the
 * group's members minus Aether/custom profiles (buildAutoSelect
 * refuses the whole group when one member does not build); one
 * member is a plain direct connect, several go through the
 * autoselect session. A chain's Aether hop needs no flag — the
 * service spots the aether:// link in the list itself (3.2.13).
 */
object StartIntent {

    fun build(context: Context, selectedId: String?): Intent? {
        if (selectedId.isNullOrEmpty()) return null
        val store = SettingsStore(context)
        val repo = ProfileRepository(context)
        val (testUrl, killSwitch) = runBlocking {
            store.testUrlNow() to store.killSwitchNow()
        }
        val profiles = repo.loadProfiles()
        val links: List<String>
        var autoSelect = false
        var autoId: String? = null
        var aether = false
        var displayName: String? = null
        when {
            selectedId.startsWith("chain:") -> {
                val chain = repo.loadChains()
                    .firstOrNull { "chain:${it.id}" == selectedId } ?: return null
                links = chain.memberIds.mapNotNull { id ->
                    profiles.firstOrNull { it.id == id }?.link
                }
                if (links.size < 2) return null
            }
            AutoSelectIds.isAuto(selectedId) -> {
                val groupKey = try {
                    AutoSelectIds.groupKeyOf(selectedId)
                } catch (_: IllegalArgumentException) {
                    return null
                }
                val members = profilesForGroup(profiles, groupKey)
                    .filter { !it.isAether && it.proto != "custom" }
                if (members.isEmpty()) return null
                if (members.size == 1) {
                    links = listOf(members[0].link)
                } else {
                    links = members.map { it.link }
                    autoSelect = true
                    autoId = selectedId
                    displayName = context.getString(
                        R.string.auto_card_title,
                        groupLabel(context, repo, groupKey),
                    )
                }
            }
            else -> {
                val profile = profiles.firstOrNull { it.id == selectedId } ?: return null
                links = listOf(profile.link)
                aether = profile.isAether
            }
        }
        return Intent(context, LiteVpnService::class.java).apply {
            action = LiteVpnService.ACTION_START
            if (links.size == 1) {
                putExtra(LiteVpnService.EXTRA_LINK, links[0])
            } else {
                putStringArrayListExtra(LiteVpnService.EXTRA_LINKS, ArrayList(links))
            }
            putExtra(LiteVpnService.EXTRA_TEST_URL, testUrl)
            putExtra(LiteVpnService.EXTRA_KILL_SWITCH, killSwitch)
            if (autoSelect) {
                putExtra(LiteVpnService.EXTRA_AUTO_SELECT, true)
                autoId?.let { putExtra(LiteVpnService.EXTRA_AUTO_ID, it) }
            }
            if (aether) putExtra(LiteVpnService.EXTRA_AETHER, true)
            displayName?.let { putExtra(LiteVpnService.EXTRA_NAME, it) }
        }
    }

    /** LiteViewModel.profilesForGroup, over a loaded list. */
    private fun profilesForGroup(profiles: List<Profile>, groupKey: String): List<Profile> =
        if (groupKey == "__all__") profiles
        else profiles.filter { it.subscriptionUrl == groupKey }

    /** LiteViewModel.groupLabel, over loaded subscriptions. */
    private fun groupLabel(context: Context, repo: ProfileRepository, groupKey: String): String =
        when (groupKey) {
            "__all__" -> context.getString(R.string.servers_group_all)
            "" -> context.getString(R.string.servers_group_manual)
            else -> repo.loadSubscriptions().firstOrNull { it.url == groupKey }?.displayName
                ?: groupKey.substringAfter("://").substringBefore("/")
        }
}
