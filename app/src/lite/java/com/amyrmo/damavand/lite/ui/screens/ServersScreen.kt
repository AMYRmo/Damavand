package com.amyrmo.damavand.lite.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.AutoSelectIds
import com.amyrmo.damavand.lite.Chain
import com.amyrmo.damavand.lite.QrShare
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.Subscription
import com.amyrmo.damavand.lite.XrayConfig
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.ChainLineIcon
import com.amyrmo.damavand.lite.ui.components.CheckLineIcon
import com.amyrmo.damavand.lite.ui.components.BoltVector
import com.amyrmo.damavand.lite.ui.components.CheckVector
import com.amyrmo.damavand.lite.ui.components.CircleIconButton
import com.amyrmo.damavand.lite.ui.components.CopyLineIcon
import com.amyrmo.damavand.lite.ui.components.EditLineIcon
import com.amyrmo.damavand.lite.ui.components.FlagBadge
import com.amyrmo.damavand.lite.ui.components.MountainContourLine
import com.amyrmo.damavand.lite.ui.components.RefreshLineIcon
import com.amyrmo.damavand.lite.ui.components.ShareLineIcon
import com.amyrmo.damavand.lite.ui.components.TrashLineIcon
import com.amyrmo.damavand.lite.ui.components.DotsVerticalVector
import com.amyrmo.damavand.lite.ui.components.InfoVector
import com.amyrmo.damavand.lite.ui.components.PlayVector
import com.amyrmo.damavand.lite.ui.components.PlayVector
import com.amyrmo.damavand.lite.ui.components.PlusVector
import com.amyrmo.damavand.lite.ui.components.RefreshVector
import com.amyrmo.damavand.lite.ui.components.SearchVector
import com.amyrmo.damavand.lite.ui.components.SortVector
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import kotlinx.coroutines.launch

internal enum class SortMode { FASTEST, NAME, NEWEST }

private data class ServerGroup(
    val key: String, // subscription URL, or "" for manual profiles
    val title: String,
    val profiles: List<Profile>,
)

/**
 * Servers tab (3.1.0, PattNG): subscriptions as collapsible groups,
 * search + sort, one ⋮ menu per row, ping as a tappable chip,
 * long-press multi-select, and the + button as a red filled circle
 * inside the title row — next to the search and sort icons, never
 * floating on its own. Fastest-first sorting only after a test
 * completes — the order is snapshotted when a test starts and never
 * re-sorts mid-test.
 */
@Composable
fun ServersScreen(
    vm: LiteViewModel,
    profiles: List<Profile>,
    selectedId: String?,
    isConnected: Boolean,
    listState: LazyListState,
    onOpenQrScan: () -> Unit = {},
    onImportFile: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {}
) {
    val isTesting by vm.isTesting.collectAsState()
    val testProgress by vm.testProgress.collectAsState()
    val testingIds by vm.testingIds.collectAsState()
    val chains by vm.chains.collectAsState()
    val chainPick by vm.chainPick.collectAsState()
    // 3.2.8: the selected group lives in the VM now (shared with
    // Home, so fastest-connect tests that group's configs) + the
    // live Auto session for the Auto card/sheet.
    val selectedGroupKey by vm.selectedGroupKey.collectAsState()
    // The no-arg StateFlow collectAsState() overload failed to resolve
    // for this flow in CI — collect it as a plain Flow with an
    // explicit initial instead (same values, same recomposition).
    val autoSession by vm.autoSession.collectAsState(initial = null)
    // 3.2.0 (PattNG): delete confirmation.
    val confirmDelete by vm.confirmDelete.collectAsState(initial = true)
    val doubleColumn by vm.doubleColumn.collectAsState(initial = false)
    val subscriptions by vm.subscriptions.collectAsState()
    val context = LocalContext.current

    var showAddSheet by remember { mutableStateOf(false) }
    var addInitialUrl by remember { mutableStateOf("") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    // 3.2.0 (PattNG): manual per-protocol editor dialog.
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    // 3.2.10 (Aether): the Aether profile editor dialog.
    var showAetherDialog by rememberSaveable { mutableStateOf(false) }
    // Search + sort live in the ViewModel: only the visible tab is
    // composed, so local state was reset on every tab switch.
    var searchQuery by vm.serversSearchQuery
    var sortMode by vm.serversSortMode
    var searchOpen by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 3.2.2: sub cards are a horizontal row — one group is
    // selected at a time and its servers show below.
    var showAutoSheet by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menuProfile by remember { mutableStateOf<Profile?>(null) }
    var menuChain by remember { mutableStateOf<Chain?>(null) }
    var editProfile by remember { mutableStateOf<Profile?>(null) }
    var shareProfile by remember { mutableStateOf<Profile?>(null) }
    var moveProfile by remember { mutableStateOf<Profile?>(null) }
    var profileToDelete by remember { mutableStateOf<Profile?>(null) }
    var profilesToDelete by remember { mutableStateOf<Set<String>?>(null) }
    var chainToDelete by remember { mutableStateOf<Chain?>(null) }
    val pickMode = chainPick.isNotEmpty()
    val multiMode = selectedIds.isNotEmpty()
    // Back leaves multi-select, chain pick or search first, instead of
    // jumping to the Home tab with the mode still active.
    BackHandler(enabled = multiMode || pickMode || searchOpen) {
        when {
            multiMode -> selectedIds = emptySet()
            pickMode -> vm.cancelChainPick()
            else -> {
                searchOpen = false
                searchQuery = ""
            }
        }
    }

    // Freeze the display order while a test runs (spec §5.2).
    var frozenIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(isTesting) {
        if (isTesting) {
            if (frozenIds == null) frozenIds = sortProfiles(profiles, sortMode).map { it.id }
        } else {
            frozenIds = null
        }
    }
    val flatList: List<Profile> = remember(profiles, frozenIds, isTesting, sortMode, searchQuery) {
        val query = searchQuery.trim()
        val filtered = if (query.isEmpty()) profiles else profiles.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true) ||
                it.country.contains(query, ignoreCase = true)
        }
        val frozen = frozenIds
        if (frozen != null) {
            val byId = filtered.associateBy { it.id }
            val ordered = frozen.mapNotNull { byId[it] }
            ordered + filtered.filter { it.id !in frozen.toSet() }
        } else {
            sortProfiles(filtered, sortMode)
        }
    }
    val groups: List<ServerGroup> = remember(flatList, subscriptions) {
        val bySub = flatList.groupBy { it.subscriptionUrl }
        val ordered = ArrayList<ServerGroup>()
        // 3.2.8: the «همه» (All) tab is always first — every config,
        // no setting gate (Amir: «یه تب all برای کل کانفیگ ها بذار»).
        if (flatList.isNotEmpty()) {
            ordered.add(ServerGroup("__all__", "", flatList))
        }
        // 3.2.12 (Amir: «یه بخش manual هم بذار»): the Manual section
        // is always there, right after All — even with no manual
        // configs yet — so the configs added apart from the subs have
        // a visible home instead of hiding at the end of the row.
        // (Only when the app holds any config at all.)
        if (flatList.isNotEmpty()) {
            ordered.add(ServerGroup("", "", bySub[""].orEmpty()))
        }
        for (sub in subscriptions) {
            val list = bySub[sub.url].orEmpty()
            if (list.isNotEmpty()) ordered.add(ServerGroup(sub.url, sub.displayName, list))
        }
        // Profiles from a subscription that no longer exists (or an
        // unknown group key) still show, under their raw URL host.
        for ((key, list) in bySub) {
            if (key.isNotEmpty() && ordered.none { it.key == key }) {
                ordered.add(ServerGroup(key, key.substringAfter("://").substringBefore("/"), list))
            }
        }
        ordered
    }
    // 3.2.12 (Damavand/ZedSecure-faithful): the group selector is a
    // row of small pills — All, Manual, the Subscriptions action,
    // then one pill per subscription — exactly like Damavand's
    // ServersScreen GroupTab row (BentoProviderCard below it shows
    // the selected subscription's detail).
    val chipEntries: List<GroupChipEntry> = remember(groups) {
        val entries = ArrayList<GroupChipEntry>()
        for (group in groups) {
            entries.add(GroupChipEntry.Group(group))
            // The Subscriptions chip sits right after Manual.
            if (group.key.isEmpty()) entries.add(GroupChipEntry.Subscriptions)
        }
        entries
    }
    val fastestId = vm.fastestProfile?.id
    val allFailed = profiles.isNotEmpty() && !isTesting &&
        profiles.all { it.lastTestedAt > 0 && it.lastPingMs <= 0 }

    // 3.2.2: the selected group — the stored group when the user
    // picked one, else the group of the selected profile, else the
    // first group (the All tab when it exists).
    val selectedGroup: ServerGroup? = remember(groups, selectedGroupKey, selectedId) {
        val byKey = selectedGroupKey?.let { key -> groups.firstOrNull { it.key == key } }
        byKey
            ?: selectedId?.let { id ->
                groups.firstOrNull { g -> g.key != "__all__" && g.profiles.any { it.id == id } }
            }
            ?: groups.firstOrNull()
    }
    // 3.2.8 (Auto): this group's Auto entry id + its live session.
    val autoId = selectedGroup?.let { AutoSelectIds.ofGroup(it.key) }
    val autoLive = autoSession?.takeIf { it.profileId == autoId }

    // 3.2.0 (PattNG): when delete confirmation is off, deletes run
    // immediately instead of showing the confirm dialog.
    fun requestDeleteProfile(p: Profile) {
        if (confirmDelete) profileToDelete = p else vm.deleteProfile(p)
    }

    fun requestDeleteProfiles(ids: Set<String>) {
        if (confirmDelete) {
            profilesToDelete = ids
        } else {
            vm.deleteProfiles(ids)
            selectedIds = emptySet()
        }
    }

    // Share plain text through the system sheet (3.2.0: export-all,
    // share-full-content).
    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    // Locate the selected profile/chain in the list (3.2.0, PattNG
    // LocateSelected; 3.2.2: horizontal cards — select its group
    // and scroll to its row. Item layout: 0 header, 1 test-all,
    // 2 chain-pick banner (pick mode only), then one item per
    // chain, then the card row, then the Auto card (when shown),
    // then the selected group's rows.
    fun locateSelected() {
        val targetId = selectedId ?: return
        searchQuery = ""
        searchOpen = false
        val banner = if (pickMode) 1 else 0
        if (targetId.startsWith("chain:")) {
            val chainIdx = chains.indexOfFirst { "chain:${it.id}" == targetId }
            if (chainIdx < 0) return
            val target = 2 + banner + chainIdx
            scope.launch { listState.scrollToItem(target) }
            return
        }
        val group = groups.firstOrNull { g ->
            g.key != "__all__" && g.profiles.any { it.id == targetId }
        } ?: return
        vm.selectGroup(group.key)
        val posInGroup = group.profiles.indexOfFirst { it.id == targetId }
        // 3.2.7: in double-column mode two profiles share one item.
        val rowPos = if (doubleColumn) posInGroup / 2 else posInGroup
        // 3.2.3: the Auto card shows for every group, so it always
        // takes one item between the card row and the rows.
        val target = 2 + banner + chains.size + 1 + 1 + rowPos
        scope.launch {
            kotlinx.coroutines.delay(150)
            listState.scrollToItem(target)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                // Title row: title + a tight action cluster (search,
                // sort, +). The + is a red filled circle — the primary
                // action, sitting with the other icons, not floating.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.tab_servers),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiteColors.text,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.weight(1f)
                    )
                    HeaderIconButton(
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        },
                        icon = SearchVector
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        HeaderIconButton(
                            onClick = { sortMenuOpen = true },
                            icon = SortVector
                        )
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            SortMenuItem(
                                labelRes = R.string.servers_sort_fastest,
                                selected = sortMode == SortMode.FASTEST,
                                onClick = { sortMode = SortMode.FASTEST; sortMenuOpen = false }
                            )
                            SortMenuItem(
                                labelRes = R.string.servers_sort_name,
                                selected = sortMode == SortMode.NAME,
                                onClick = { sortMode = SortMode.NAME; sortMenuOpen = false }
                            )
                            SortMenuItem(
                                labelRes = R.string.servers_sort_newest,
                                selected = sortMode == SortMode.NEWEST,
                                onClick = { sortMode = SortMode.NEWEST; sortMenuOpen = false }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // More menu (3.2.0, PattNG): real-ping test-all,
                    // restart service, locate selected, update subs,
                    // export all, delete duplicates/invalid/all.
                    Box {
                        HeaderIconButton(
                            onClick = { moreMenuOpen = true },
                            icon = DotsVerticalVector
                        )
                        DropdownMenu(
                            expanded = moreMenuOpen,
                            onDismissRequest = { moreMenuOpen = false }
                        ) {
                            MenuActionItem(
                                labelRes = R.string.servers_menu_real_ping_all,
                                enabled = !isTesting && !isConnected,
                                onClick = { moreMenuOpen = false; vm.testAllRealPing() }
                            )
                            MenuActionItem(
                                labelRes = R.string.servers_menu_restart,
                                enabled = !isTesting,
                                onClick = { moreMenuOpen = false; vm.restartService() }
                            )
                            MenuActionItem(
                                labelRes = R.string.servers_menu_locate,
                                enabled = selectedId != null,
                                onClick = { moreMenuOpen = false; locateSelected() }
                            )
                            MenuActionItem(
                                labelRes = R.string.servers_menu_update_subs,
                                enabled = !isTesting,
                                onClick = { moreMenuOpen = false; vm.refreshAllSubscriptions() }
                            )
                            MenuActionItem(
                                labelRes = R.string.servers_menu_export_all,
                                enabled = profiles.isNotEmpty(),
                                onClick = { moreMenuOpen = false; shareText(vm.exportAllText()) }
                            )
                            MenuActionItem(
                                labelRes = R.string.servers_menu_delete_dupes,
                                enabled = profiles.isNotEmpty(),
                                onClick = { moreMenuOpen = false; vm.deleteDuplicateProfiles() }
                            )
                            MenuActionItem(
                                labelRes = R.string.servers_menu_delete_invalid,
                                enabled = profiles.isNotEmpty(),
                                onClick = { moreMenuOpen = false; vm.deleteInvalidProfiles() }
                            )
                            MenuActionItem(
                                labelRes = R.string.servers_menu_delete_all,
                                enabled = profiles.isNotEmpty(),
                                danger = true,
                                onClick = {
                                    moreMenuOpen = false
                                    requestDeleteProfiles(profiles.map { it.id }.toSet())
                                }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        buttonSize = 42.dp,
                        glyphSize = 20.dp,
                        filled = true,
                        onClick = { showAddSheet = true },
                        glyphColor = Color.White,
                        icon = PlusVector
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.servers_search_hint),
                                fontSize = 14.sp,
                                color = LiteColors.textMuted,
                                fontFamily = Vazirmatn
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = Vazirmatn,
                            fontSize = 14.sp,
                            color = LiteColors.text
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LiteColors.hairlineStrong,
                            unfocusedBorderColor = LiteColors.hairline,
                            cursorColor = LiteColors.accent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                // «تست همه سرورها» — full width, 48dp, pill, strong border.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .alpha(if (isTesting || profiles.isEmpty()) 0.5f else 1f)
                        .background(Color.Transparent, CircleShape)
                        .border(1.5.dp, LiteColors.hairlineStrong, CircleShape)
                        .clickable(
                            enabled = !isTesting && profiles.isNotEmpty(),
                            onClick = { vm.testAll() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (!isTesting) {
                            RefreshLineIcon(size = 20.dp, color = LiteColors.text)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(
                                if (isTesting) R.string.btn_testing else R.string.btn_test_all
                            ),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn
                        )
                    }
                }
                // Thin red progress hairline (2dp) while testing.
                if (isTesting) {
                    val (done, total) = testProgress
                    val fraction = if (total > 0) done.toFloat() / total else 0f
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(LiteColors.hairline)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(2.dp)
                                .background(LiteColors.accent)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.test_note),
                    fontSize = 12.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn
                )
                if (profiles.isNotEmpty() && !pickMode && !multiMode) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.servers_chain_hint),
                        fontSize = 12.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn
                    )
                }
                if (allFailed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.servers_test_all_failed),
                        fontSize = 12.sp,
                        color = LiteColors.error,
                        fontFamily = Vazirmatn
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            if (pickMode) {
                item {
                    ChainPickBanner(
                        count = chainPick.size,
                        onCreate = { vm.createChainFromPick() },
                        onCancel = { vm.cancelChainPick() }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            items(chains, key = { "chain:${it.id}" }) { chain ->
                val names = chain.memberIds.mapNotNull { id ->
                    profiles.firstOrNull { it.id == id }?.name
                }
                ChainRow(
                    chain = chain,
                    memberNames = names,
                    isSelected = selectedId == "chain:${chain.id}",
                    onSelect = { vm.selectChain(chain.id) },
                    onMenu = { menuChain = chain }
                )
                Spacer(Modifier.height(12.dp))
            }
            if (profiles.isEmpty() && chains.isEmpty()) {
                item {
                    // Empty state (§5.5): contour art + title + body + add button.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MountainContourLine(
                            modifier = Modifier
                                .width(120.dp)
                                .height(24.dp),
                            color = LiteColors.textMuted.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.servers_empty_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.servers_empty_body),
                            fontSize = 13.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(280.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.servers_add_subscription),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(onClick = { showAddSheet = true })
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        )
                    }
                }
            } else {
                // 3.2.12 (Damavand/ZedSecure-faithful): the group
                // selector is a row of SMALL pills — All, Manual,
                // Subscriptions, one per subscription — exactly like
                // Damavand's GroupTab row. Tap a pill to select it;
                // long-press a subscription pill for its menu
                // (Update / Subscriptions). The selected
                // subscription's detail card shows right below.
                item(key = "group-chips") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(chipEntries, key = {
                            when (it) {
                                is GroupChipEntry.Group -> "chip:${it.group.key}"
                                GroupChipEntry.Subscriptions -> "chip:subs"
                            }
                        }) { entry ->
                            when (entry) {
                                is GroupChipEntry.Group -> {
                                    val group = entry.group
                                    val isSub = subscriptions.any { it.url == group.key }
                                    var chipMenuOpen by remember { mutableStateOf(false) }
                                    Box {
                                        GroupChip(
                                            label = when {
                                                group.key == "__all__" -> stringResource(R.string.servers_group_all)
                                                group.key.isEmpty() -> stringResource(R.string.servers_group_manual)
                                                else -> group.title
                                            },
                                            count = group.profiles.size,
                                            selected = group.key == selectedGroup?.key,
                                            onClick = { vm.selectGroup(group.key) },
                                            onMenu = if (isSub) {
                                                { chipMenuOpen = true }
                                            } else {
                                                null
                                            }
                                        )
                                        if (isSub) {
                                            DropdownMenu(
                                                expanded = chipMenuOpen,
                                                onDismissRequest = { chipMenuOpen = false }
                                            ) {
                                                MenuActionItem(
                                                    labelRes = R.string.subs_update,
                                                    enabled = true,
                                                    onClick = {
                                                        chipMenuOpen = false
                                                        vm.updateSubscription(group.key)
                                                    }
                                                )
                                                MenuActionItem(
                                                    labelRes = R.string.subs_title,
                                                    enabled = true,
                                                    onClick = {
                                                        chipMenuOpen = false
                                                        onOpenSubscriptions()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                GroupChipEntry.Subscriptions -> {
                                    GroupChip(
                                        label = stringResource(R.string.subs_title),
                                        count = null,
                                        selected = false,
                                        onClick = onOpenSubscriptions
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // 3.2.12 (Damavand/ZedSecure-faithful): when a
                // subscription group is selected, its provider card
                // shows here — Damavand's BentoProviderCard, violet
                // with the sync button, usage bar and Support /
                // Website / Subscriptions links.
                val openSub = selectedGroup?.let { g ->
                    subscriptions.firstOrNull { it.url == g.key }
                }
                if (openSub != null) {
                    item(key = "provider-${openSub.url}") {
                        SubscriptionCard(
                            subscription = openSub,
                            count = selectedGroup!!.profiles.size,
                            onRefresh = { vm.updateSubscription(openSub.url) },
                            onOpenSubscriptions = onOpenSubscriptions,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                // 3.2.8 (Auto, Damavand-faithful): the Auto card is a
                // SELECTABLE server row for the selected group — tap to
                // select it, play to connect through it, (i) for the
                // live members + switch history. The core measures the
                // members and switches by itself; there is no toggle.
                val autoGroup = selectedGroup
                if (autoGroup != null && autoId != null) {
                    item(key = "auto-card") {
                        AutoSelectCard(
                            groupLabel = vm.groupLabel(autoGroup.key),
                            memberCount = autoGroup.profiles.size,
                            active = selectedId == autoId,
                            live = autoLive,
                            memberName = { id -> profiles.firstOrNull { it.id == id }?.name },
                            onSelect = { vm.selectAuto(autoId) },
                            onConnect = { vm.connectToAuto(autoId) },
                            onDetails = { showAutoSheet = true }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                val shownGroup = selectedGroup
                if (shownGroup != null) {
                    if (doubleColumn) {
                        // 3.2.7 (PattNG): two profiles per row, same
                        // ServerRow at half width.
                        val pairs = shownGroup.profiles.chunked(2)
                        items(pairs, key = { it.first().id }) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (profile in pair) {
                                    ServerRow(
                                        modifier = Modifier.weight(1f),
                                        profile = profile,
                                        isSelected = profile.id == selectedId,
                                        isFastest = profile.id == fastestId,
                                        isTesting = profile.id in testingIds,
                                        pickMode = pickMode,
                                        chainOrder = chainPick.indexOf(profile.id).takeIf { it >= 0 }?.plus(1),
                                        multiSelected = profile.id in selectedIds,
                                        multiMode = multiMode,
                                        onSelect = { vm.selectProfile(profile.id) },
                                        onPickToggle = { vm.toggleChainPick(profile.id) },
                                        onMultiToggle = {
                                            selectedIds = if (profile.id in selectedIds) selectedIds - profile.id
                                            else selectedIds + profile.id
                                        },
                                        onPing = { vm.testOne(profile) },
                                        onMenu = { menuProfile = profile }
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    } else {
                    items(shownGroup.profiles, key = { it.id }) { profile ->
                        ServerRow(
                            profile = profile,
                            isSelected = profile.id == selectedId,
                            isFastest = profile.id == fastestId,
                            isTesting = profile.id in testingIds,
                            pickMode = pickMode,
                            chainOrder = chainPick.indexOf(profile.id).takeIf { it >= 0 }?.plus(1),
                            multiSelected = profile.id in selectedIds,
                            multiMode = multiMode,
                            onSelect = { vm.selectProfile(profile.id) },
                            onPickToggle = { vm.toggleChainPick(profile.id) },
                            onMultiToggle = {
                                selectedIds = if (profile.id in selectedIds) selectedIds - profile.id
                                else selectedIds + profile.id
                            },
                            onPing = { vm.testOne(profile) },
                            onMenu = { menuProfile = profile }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }

        // Multi-select bottom bar (3.1.0, PattNG).
        if (multiMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LiteColors.surface)
                    .border(1.dp, LiteColors.hairline)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.multiselect_count,
                        LiteFormat.toPersian(selectedIds.size.toString())
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.multiselect_test),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    modifier = Modifier
                        .clickable {
                            selectedIds.forEach { id ->
                                profiles.firstOrNull { it.id == id }?.let { vm.testOne(it) }
                            }
                            selectedIds = emptySet()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.multiselect_delete),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.error,
                    fontFamily = Vazirmatn,
                    modifier = Modifier
                        .clickable { requestDeleteProfiles(selectedIds) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.multiselect_cancel),
                    fontSize = 13.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    modifier = Modifier
                        .clickable { selectedIds = emptySet() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }

    // ---- Add sheet (3.1.0, PattNG) ----
    if (showAddSheet) {
        AlertDialog(
            onDismissRequest = { showAddSheet = false },
            title = {
                Text(
                    text = stringResource(R.string.add_sheet_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            },
            text = {
                Column {
                    AddSheetRow(
                        labelRes = R.string.add_from_clipboard,
                        onClick = {
                            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                ?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            showAddSheet = false
                            if (clip.contains("://")) {
                                addInitialUrl = clip.trim()
                                showAddDialog = true
                            } else {
                                Toast.makeText(context, context.getString(R.string.add_clipboard_empty), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    AddSheetRow(
                        labelRes = R.string.add_manual,
                        onClick = {
                            showAddSheet = false
                            addInitialUrl = ""
                            showAddDialog = true
                        }
                    )
                    AddSheetRow(
                        labelRes = R.string.add_subscription,
                        onClick = {
                            showAddSheet = false
                            addInitialUrl = ""
                            showAddDialog = true
                        }
                    )
                    // 3.2.0 (PattNG): QR scan import.
                    AddSheetRow(
                        labelRes = R.string.add_qr_scan,
                        onClick = {
                            showAddSheet = false
                            onOpenQrScan()
                        }
                    )
                    // 3.2.11 (PattNG ImportConfigLocal): import from a file.
                    AddSheetRow(
                        labelRes = R.string.add_from_file,
                        onClick = {
                            showAddSheet = false
                            onImportFile()
                        }
                    )
                    // 3.2.0 (PattNG): manual per-protocol editor.
                    AddSheetRow(
                        labelRes = R.string.add_manual_build,
                        onClick = {
                            showAddSheet = false
                            showManualDialog = true
                        }
                    )
                    // 3.2.10 (Aether): a standalone Aether profile.
                    AddSheetRow(
                        labelRes = R.string.add_aether,
                        onClick = {
                            showAddSheet = false
                            showAetherDialog = true
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddSheet = false }) {
                    Text(
                        text = stringResource(R.string.sub_import_cancel),
                        fontSize = 14.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn
                    )
                }
            },
            containerColor = LiteColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            vm = vm,
            initialUrl = addInitialUrl,
            onDismiss = { showAddDialog = false }
        )
    }

    // 3.2.0 (PattNG): manual per-protocol editor.
    if (showManualDialog) {
        ManualConfigDialog(vm = vm, onDismiss = { showManualDialog = false })
    }

    // 3.2.10 (Aether): a standalone Aether profile.
    if (showAetherDialog) {
        AetherAddDialog(vm = vm, onDismiss = { showAetherDialog = false })
    }

    // ---- Row ⋮ menu (3.1.0, PattNG) ----
    val menuTarget = menuProfile
    if (menuTarget != null) {
        ProfileMenu(
            profile = menuTarget,
            onDismiss = { menuProfile = null },
            onEdit = { editProfile = menuTarget; menuProfile = null },
            onDuplicate = { vm.duplicateProfile(menuTarget); menuProfile = null },
            onShare = { shareProfile = menuTarget; menuProfile = null },
            onShareFull = { shareText(menuTarget.link); menuProfile = null },
            onTest = { vm.testOne(menuTarget); menuProfile = null },
            onMove = { moveProfile = menuTarget; menuProfile = null },
            onDelete = { requestDeleteProfile(menuTarget); menuProfile = null }
        )
    }
    val chainMenuTarget = menuChain
    if (chainMenuTarget != null) {
        AlertDialog(
            onDismissRequest = { menuChain = null },
            title = {
                Text(
                    text = chainMenuTarget.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            },
            text = {
                Column {
                    AddSheetRow(
                        labelRes = R.string.server_menu_connect,
                        onClick = { vm.connectToChain(chainMenuTarget); menuChain = null }
                    )
                    AddSheetRow(
                        labelRes = R.string.server_menu_delete,
                        onClick = {
                            menuChain = null
                            if (confirmDelete) chainToDelete = chainMenuTarget
                            else vm.deleteChain(chainMenuTarget.id)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuChain = null }) {
                    Text(
                        text = stringResource(R.string.sub_import_cancel),
                        fontSize = 14.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn
                    )
                }
            },
            containerColor = LiteColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ---- Edit dialog (3.2.8, PattNG): the structured per-protocol
    // editor when the link parses (address, port, UUID/password,
    // security, fingerprint, ALPN… — like ServerVlessActivity); the
    // raw link box stays as the fallback for unparseable links.
    val editing = editProfile
    if (editing != null) {
        // 3.2.10 (Aether): an Aether profile edits its settings,
        // not a link — the link is just its stable id.
        if (editing.isAether) {
            AetherEditDialog(
                profile = editing,
                vm = vm,
                onDismiss = { editProfile = null }
            )
        } else {
        val parsed = XrayConfig.parse(editing.link)
        if (parsed != null && parsed.proto != "custom") {
            EditConfigDialog(
                profile = editing,
                onDismiss = { editProfile = null },
                onSave = { name, link, onResult ->
                    vm.updateProfileLink(editing.id, link) { ok ->
                        if (ok) {
                            vm.renameProfile(editing.id, name)
                            editProfile = null
                        }
                        onResult(ok)
                    }
                }
            )
        } else {
            EditProfileDialog(
                profile = editing,
                onDismiss = { editProfile = null },
                onSave = { name, link, onResult ->
                    vm.updateProfileLink(editing.id, link) { ok ->
                        if (ok) {
                            vm.renameProfile(editing.id, name)
                            editProfile = null
                        }
                        onResult(ok)
                    }
                }
            )
        }
        }
    }

    // ---- Share dialog with QR (3.1.0, PattNG) ----
    val sharing = shareProfile
    if (sharing != null) {
        ShareProfileDialog(
            profile = sharing,
            onDismiss = { shareProfile = null }
        )
    }

    // ---- Move dialog (3.1.0, PattNG) ----
    val moving = moveProfile
    if (moving != null) {
        MoveProfileDialog(
            profile = moving,
            subscriptions = subscriptions,
            onDismiss = { moveProfile = null },
            onMove = { url ->
                vm.moveProfile(moving.id, url)
                moveProfile = null
            }
        )
    }

    val deletingProfile = profileToDelete
    if (deletingProfile != null) {
        DeleteConfirmDialog(
            title = stringResource(R.string.server_delete_title),
            body = stringResource(R.string.server_delete_body, deletingProfile.name),
            onConfirm = {
                vm.deleteProfile(deletingProfile)
                profileToDelete = null
            },
            onDismiss = { profileToDelete = null }
        )
    }
    val deletingMany = profilesToDelete
    if (deletingMany != null) {
        DeleteConfirmDialog(
            title = stringResource(R.string.server_delete_multi_title),
            body = stringResource(R.string.server_delete_multi_body, LiteFormat.toPersian(deletingMany.size.toString())),
            onConfirm = {
                vm.deleteProfiles(deletingMany)
                profilesToDelete = null
                selectedIds = emptySet()
            },
            onDismiss = { profilesToDelete = null }
        )
    }
    val deletingChain = chainToDelete
    if (deletingChain != null) {
        DeleteConfirmDialog(
            title = stringResource(R.string.server_delete_title),
            body = stringResource(R.string.server_delete_body, deletingChain.name),
            onConfirm = {
                vm.deleteChain(deletingChain.id)
                chainToDelete = null
            },
            onDismiss = { chainToDelete = null }
        )
    }

    // 3.2.8 (Auto): the details sheet — live exit, switch history,
    // per-member health, pinning.
    if (showAutoSheet && selectedGroup != null && autoId != null) {
        AutoSelectSheet(
            groupLabel = vm.groupLabel(selectedGroup.key),
            members = selectedGroup.profiles,
            live = autoLive,
            onDismiss = { showAutoSheet = false },
            onPin = { tag -> vm.pinAutoMember(tag) },
            onUnpin = { vm.unpinAutoMember() }
        )
    }
}

/** Sorting for the flat list: fastest first after a test, name, or newest. */
private fun sortProfiles(list: List<Profile>, mode: SortMode): List<Profile> {
    return when (mode) {
        SortMode.FASTEST -> {
            if (list.none { it.lastPingMs > 0 }) list
            else list.sortedWith(compareBy({ it.lastPingMs <= 0 }, { it.lastPingMs }))
        }
        SortMode.NAME -> list.sortedBy { it.name }
        SortMode.NEWEST -> list.reversed()
    }
}

// ---- Header icon button (3.1.2: vector icon via CircleIconButton) ----

@Composable
private fun HeaderIconButton(onClick: () -> Unit, icon: ImageVector) {
    CircleIconButton(
        buttonSize = 40.dp,
        glyphSize = 18.dp,
        filled = false,
        onClick = onClick,
        glyphColor = LiteColors.text,
        icon = icon
    )
}

@Composable
private fun MenuActionItem(labelRes: Int, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                fontSize = 14.sp,
                color = if (danger) LiteColors.error else LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        onClick = onClick,
        enabled = enabled
    )
}

@Composable
private fun SortMenuItem(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(labelRes),
                    fontSize = 14.sp,
                    color = if (selected) LiteColors.accent else LiteColors.text,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.weight(1f)
                )
                if (selected) CheckLineIcon(size = 16.dp, color = LiteColors.accent)
            }
        },
        onClick = onClick
    )
}

@Composable
private fun AddSheetRow(labelRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.text,
            fontFamily = Vazirmatn
        )
    }
}

// ---- Group header (3.1.0, PattNG) ----

// ---- Subscription detail card (3.2.0) ----

/**
 * A subscription card in the app's own theme (dark surface, red
 * accent — NOT the purple/lime of the reference screenshot): name,
 * server count + last update, data used with a progress bar, and
 * expiry when the panel reports them. Tap collapses the group;
 * the refresh / balancer actions live on the card itself.
 */
// 3.2.12 (Damavand ServersScreen GroupTab): one entry of the group
// pill row — a group, or the Subscriptions action chip.
private sealed interface GroupChipEntry {
    data class Group(val group: ServerGroup) : GroupChipEntry
    data object Subscriptions : GroupChipEntry
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LiteColors.accent else LiteColors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count != null) "$label  ${LiteFormat.toPersian(count.toString())}" else label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 3.2.12 (Damavand/ZedSecure-faithful): the selected subscription's
// provider card — Damavand's BentoProviderCard layout and behaviour
// (title, "N servers · updated X ago", sync button, usage bar,
// usage + expiry, Support / Website / Subscriptions link chips;
// tap the card body to manage the subscription). Its colours are
// Lite's own (surface card, Damavand Red accent), NOT Damavand's
// violet Bento tone — Amir: «رنگش باید هماهنگ با رنگ خود اپ باشه».
@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    count: Int,
    onRefresh: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val used = subscription.usedBytes
    val total = subscription.total
    val hasUsage = used > 0 || total > 0
    val fraction = if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    val exhausted = fraction != null && fraction >= 1f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(LiteColors.surface)
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(28.dp))
            .clickable(onClick = onOpenSubscriptions)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = subscription.displayName,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.servers_group_count,
                        LiteFormat.toPersian(count.toString())
                    ) + " · " + updatedAgoText(subscription.lastUpdated),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LiteColors.accent)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = RefreshVector,
                    contentDescription = stringResource(R.string.subs_update),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (fraction != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LiteColors.hairline)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (exhausted) LiteColors.error else LiteColors.accent)
                )
            }
        }
        if (hasUsage) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.sub_card_used,
                        LiteFormat.formatVolume(used)
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (total > 0) {
                        stringResource(
                            R.string.sub_card_remaining,
                            LiteFormat.formatVolume((total - used).coerceAtLeast(0))
                        )
                    } else {
                        stringResource(R.string.sub_card_unlimited)
                    },
                    fontSize = 12.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn,
                    maxLines = 1
                )
            }
        }
        if (subscription.expire > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.sub_card_expires,
                    LiteFormat.toPersian(
                        java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
                            .format(java.util.Date(subscription.expire * 1000))
                    )
                ),
                fontSize = 12.sp,
                color = LiteColors.textMuted,
                fontFamily = Vazirmatn
            )
        }
        // 3.2.2: like main Damavand — Support / Website only show
        // when the sub itself reports them (profile-web-page-url /
        // support-url in the userinfo header); when the panel sends
        // none, the buttons are hidden instead of opening the sub's
        // origin. Subscriptions always shows — it opens the in-app
        // subscriptions screen, not a URL.
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (subscription.supportUrl.isNotBlank()) {
                SubCardButton(
                    label = stringResource(R.string.sub_card_support),
                    onClick = { openUrl(context, subscription.supportUrl) }
                )
            }
            if (subscription.websiteUrl.isNotBlank()) {
                SubCardButton(
                    label = stringResource(R.string.sub_card_website),
                    onClick = { openUrl(context, subscription.websiteUrl) }
                )
            }
            SubCardButton(
                label = stringResource(R.string.sub_card_subscriptions),
                onClick = onOpenSubscriptions
            )
        }
    }
}

// 3.2.12: the link chips on the provider card — Lite's own pill
// style (surface2, muted text), matching the rest of the app.
@Composable
private fun SubCardButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(LiteColors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            maxLines = 1
        )
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // No browser / bad URL — the button just does nothing.
    }
}

@Composable
private fun updatedAgoText(lastUpdated: Long): String {
    if (lastUpdated <= 0) return stringResource(R.string.sub_card_updated_never)
    val minutes = (System.currentTimeMillis() - lastUpdated) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.sub_card_updated_now)
        minutes < 60 -> stringResource(
            R.string.sub_card_updated_minutes,
            LiteFormat.toPersian(minutes.toString())
        )
        minutes < 60 * 24 -> stringResource(
            R.string.sub_card_updated_hours,
            LiteFormat.toPersian((minutes / 60).toString())
        )
        else -> stringResource(
            R.string.sub_card_updated_days,
            LiteFormat.toPersian((minutes / (60 * 24)).toString())
        )
    }
}

// 3.2.2: compact card for the manual / «همه» groups in the
// horizontal card row — same shape as SubscriptionCard.
@Composable
private fun ChainPickBanner(
    count: Int,
    onCreate: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, LiteColors.hairlineStrong, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.chain_pick_title, LiteFormat.toPersian(count.toString())),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.text,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.chain_pick_body),
            fontSize = 12.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.chain_create),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (count >= 2) LiteColors.accent else LiteColors.textMuted,
                fontFamily = Vazirmatn,
                modifier = Modifier
                    .alpha(if (count >= 2) 1f else 0.5f)
                    .clickable(enabled = count >= 2, onClick = onCreate)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
            Text(
                text = stringResource(R.string.chain_cancel),
                fontSize = 14.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }
    }
}

// ---- Chain row (3.0.0, PattNG chain; 3.1.0: ⋮ menu instead of trash) ----

@Composable
private fun ChainRow(
    chain: Chain,
    memberNames: List<String>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (isSelected) LiteColors.hairlineStrong else LiteColors.hairline,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Same radio as a server row.
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.5.dp, LiteColors.hairlineStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(LiteColors.accent, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        ChainLineIcon(size = 22.dp, color = LiteColors.text)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chain.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val subtitle = buildString {
                append(stringResource(R.string.chain_members, LiteFormat.toPersian(memberNames.size.toString())))
                if (memberNames.isNotEmpty()) {
                    append(" — ")
                    append(memberNames.take(2).joinToString(if (LiteFormat.persianDigits) "، " else ", "))
                    if (memberNames.size > 2) append(if (LiteFormat.persianDigits) " و …" else " & …")
                }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        CircleIconButton(
            buttonSize = 36.dp,
            glyphSize = 18.dp,
            filled = false,
            onClick = onMenu,
            glyphColor = LiteColors.text,
            icon = DotsVerticalVector
        )
    }
}

// ---- Server row (§5.2; 3.1.0: ping chip + one ⋮ menu) ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerRow(
    modifier: Modifier = Modifier,
    profile: Profile,
    isSelected: Boolean,
    isFastest: Boolean,
    isTesting: Boolean,
    pickMode: Boolean,
    chainOrder: Int?,
    multiSelected: Boolean,
    multiMode: Boolean,
    onSelect: () -> Unit,
    onPickToggle: () -> Unit,
    onMultiToggle: () -> Unit,
    onPing: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (isSelected || multiSelected) LiteColors.hairlineStrong else LiteColors.hairline,
                RoundedCornerShape(20.dp)
            )
            .combinedClickable(
                onClick = {
                    when {
                        multiMode -> onMultiToggle()
                        pickMode -> onPickToggle()
                        else -> onSelect()
                    }
                },
                onLongClick = {
                    if (pickMode) onPickToggle() else onMultiToggle()
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Radio 20dp: outer circle; selected = red dot 10dp inside.
        // In chain-pick mode it shows the pick order instead; in
        // multi-select mode a red check.
        if (multiMode) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        if (multiSelected) LiteColors.accent else Color.Transparent,
                        CircleShape
                    )
                    .border(1.5.dp, LiteColors.hairlineStrong, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (multiSelected) CheckLineIcon(size = 12.dp, color = Color.White)
            }
        } else if (pickMode && chainOrder != null) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(LiteColors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = LiteFormat.toPersian(chainOrder.toString()),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, LiteColors.hairlineStrong, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(LiteColors.accent, CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        FlagBadge(countryCode = profile.countryCode.ifBlank { null }, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val location = listOf(profile.city, profile.country)
                    .filter { it.isNotBlank() }
                    .joinToString(if (LiteFormat.persianDigits) "، " else ", ")
                if (location.isNotBlank()) {
                    Text(
                        text = location,
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                CoreTag(core = profile.core)
                if (isFastest) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.badge_fastest),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiteColors.accent,
                        fontFamily = Vazirmatn
                    )
                }
            }
        }
        // Ping chip (3.1.0, PattNG): a colored value chip; tap to re-test.
        Spacer(Modifier.width(8.dp))
        PingChip(
            profile = profile,
            isTesting = isTesting,
            onPing = onPing
        )
        // One ⋮ menu per row (3.1.0): edit / duplicate / share /
        // test / move / delete — no standalone trash or refresh icon.
        Spacer(Modifier.width(8.dp))
        CircleIconButton(
            buttonSize = 36.dp,
            glyphSize = 18.dp,
            filled = false,
            onClick = onMenu,
            glyphColor = LiteColors.text,
            icon = DotsVerticalVector
        )
    }
}

/** Ping as a colored chip: green <150, amber <400, red above. */
@Composable
private fun PingChip(profile: Profile, isTesting: Boolean, onPing: () -> Unit) {
    val (text, color) = when {
        isTesting -> "…" to LiteColors.textMuted
        profile.lastTestedAt == 0L -> stringResource(R.string.server_ping) to LiteColors.textMuted
        profile.lastPingMs < 0 -> stringResource(R.string.ping_failed) to LiteColors.error
        profile.lastPingMs < 150 -> LiteFormat.formatPing(profile.lastPingMs) to LiteColors.green
        profile.lastPingMs < 400 -> LiteFormat.formatPing(profile.lastPingMs) to LiteColors.textSecondary
        else -> LiteFormat.formatPing(profile.lastPingMs) to LiteColors.error
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .clickable(enabled = !isTesting, onClick = onPing)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = Vazirmatn,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1
        )
    }
}

// ---- Row ⋮ menu (3.1.0, PattNG) ----

@Composable
private fun ProfileMenu(
    profile: Profile,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onShareFull: () -> Unit,
    onTest: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    // A plain dialog is used instead of DropdownMenu: the menu must
    // stay open above the scrolling list, and a dialog is the robust
    // way to do that in a LazyColumn row.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = profile.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                MenuRow(icon = { EditLineIcon(size = 18.dp, color = LiteColors.text) }, labelRes = R.string.server_menu_edit, onClick = onEdit)
                MenuRow(icon = { CopyLineIcon(size = 18.dp, color = LiteColors.text) }, labelRes = R.string.server_menu_duplicate, onClick = onDuplicate)
                MenuRow(icon = { ShareLineIcon(size = 18.dp, color = LiteColors.text) }, labelRes = R.string.server_menu_share, onClick = onShare)
                MenuRow(icon = { ShareLineIcon(size = 18.dp, color = LiteColors.text) }, labelRes = R.string.server_menu_share_full, onClick = onShareFull)
                MenuRow(icon = { RefreshLineIcon(size = 18.dp, color = LiteColors.text) }, labelRes = R.string.server_menu_test, onClick = onTest)
                MenuRow(icon = { ChainLineIcon(size = 18.dp, color = LiteColors.text) }, labelRes = R.string.server_menu_move, onClick = onMove)
                MenuRow(icon = { TrashLineIcon(size = 18.dp, color = LiteColors.error) }, labelRes = R.string.server_menu_delete, danger = true, onClick = onDelete)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sub_import_cancel),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun MenuRow(
    icon: @Composable () -> Unit,
    labelRes: Int,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(labelRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) LiteColors.error else LiteColors.text,
            fontFamily = Vazirmatn
        )
    }
}

// ---- Edit dialog (3.1.0, PattNG) ----

@Composable
private fun EditProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (String, String, (Boolean) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var link by remember { mutableStateOf(profile.link) }
    var linkError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.server_edit_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(
                            text = stringResource(R.string.server_edit_name),
                            fontSize = 12.sp,
                            color = LiteColors.textMuted,
                            fontFamily = Vazirmatn
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Vazirmatn, fontSize = 14.sp, color = LiteColors.text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiteColors.hairlineStrong,
                        unfocusedBorderColor = LiteColors.hairline,
                        cursorColor = LiteColors.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; linkError = false },
                    label = {
                        Text(
                            text = stringResource(R.string.server_edit_link),
                            fontSize = 12.sp,
                            color = LiteColors.textMuted,
                            fontFamily = Vazirmatn
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    isError = linkError,
                    textStyle = TextStyle(
                        fontFamily = Vazirmatn,
                        fontSize = 13.sp,
                        color = LiteColors.text,
                        textDirection = TextDirection.Ltr
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiteColors.hairlineStrong,
                        unfocusedBorderColor = LiteColors.hairline,
                        errorBorderColor = LiteColors.error,
                        cursorColor = LiteColors.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (linkError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.server_edit_invalid),
                        fontSize = 12.sp,
                        color = LiteColors.error,
                        fontFamily = Vazirmatn
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!link.contains("://")) {
                        linkError = true
                        return@TextButton
                    }
                    onSave(name, link) { ok ->
                        if (!ok) linkError = true
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.server_edit_save),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sub_import_cancel),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

// ---- Share dialog with QR (3.1.0, PattNG) ----

@Composable
private fun ShareProfileDialog(profile: Profile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val qr = remember(profile.link) { QrShare.bitmap(profile.link, 512) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.server_share_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = profile.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clip?.setPrimaryClip(ClipData.newPlainText("damavand-lite", profile.link))
                    Toast.makeText(context, context.getString(R.string.server_share_copy), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.server_share_copy),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, profile.link)
                    }
                    context.startActivity(Intent.createChooser(intent, profile.name))
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.server_share_send),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

// ---- Move dialog (3.1.0, PattNG) ----

@Composable
private fun MoveProfileDialog(
    profile: Profile,
    subscriptions: List<Subscription>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.server_move_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column {
                MoveRow(
                    label = stringResource(R.string.servers_group_manual),
                    selected = profile.subscriptionUrl.isEmpty(),
                    onClick = { onMove("") }
                )
                for (sub in subscriptions) {
                    MoveRow(
                        label = sub.displayName,
                        selected = profile.subscriptionUrl == sub.url,
                        onClick = { onMove(sub.url) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sub_import_cancel),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun MoveRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.5.dp, LiteColors.hairlineStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(LiteColors.accent, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.text,
            fontFamily = Vazirmatn,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---- Delete confirmation (3.0.0, PattNG) ----

@Composable
private fun DeleteConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Text(
                text = body,
                fontSize = 14.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.server_delete_confirm),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.error,
                    fontFamily = Vazirmatn
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sub_import_cancel),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

// ---- Subscription import (one quiet dialog, spec §8.5) ----

@Composable
fun AddSubscriptionDialog(
    vm: LiteViewModel,
    initialUrl: String = "",
    onDismiss: () -> Unit
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    val subImport by vm.subImport.collectAsState()
    val adding = subImport == LiteViewModel.SubImportState.Adding

    LaunchedEffect(subImport) {
        if (subImport == LiteViewModel.SubImportState.Added) {
            vm.resetSubImport()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!adding) {
                vm.resetSubImport()
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(R.string.sub_import_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.sub_import_hint),
                            fontSize = 14.sp,
                            color = LiteColors.textMuted,
                            fontFamily = Vazirmatn
                        )
                    },
                    singleLine = true,
                    enabled = !adding,
                    textStyle = TextStyle(
                        fontFamily = Vazirmatn,
                        fontSize = 14.sp,
                        color = LiteColors.text,
                        textDirection = TextDirection.Ltr
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiteColors.hairlineStrong,
                        unfocusedBorderColor = LiteColors.hairline,
                        disabledBorderColor = LiteColors.hairline,
                        cursorColor = LiteColors.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                // While adding, show that something IS happening (2.0.2:
                // on-device 2.0.0 the dialog sat still with no spinner,
                // so it looked as if the download never started).
                if (adding) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LiteColors.accent
                        )
                        Text(
                            text = stringResource(R.string.sub_import_fetching),
                            fontSize = 12.sp,
                            color = LiteColors.textSecondary,
                            fontFamily = Vazirmatn
                        )
                    }
                }
                // `subImport` is a delegated property, so it cannot be
                // smart-cast after an `is` check — capture it first.
                val failed = subImport as? LiteViewModel.SubImportState.Failed
                if (failed != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (failed.count > 0) {
                            stringResource(failed.resId, failed.count)
                        } else {
                            stringResource(failed.resId)
                        },
                        fontSize = 12.sp,
                        color = LiteColors.error,
                        fontFamily = Vazirmatn
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.addSubscription(url.trim()) },
                enabled = url.isNotBlank() && !adding
            ) {
                Text(
                    text = stringResource(R.string.sub_import_add),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    vm.resetSubImport()
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.sub_import_cancel),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
