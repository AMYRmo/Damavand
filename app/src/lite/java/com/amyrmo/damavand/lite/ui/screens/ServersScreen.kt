package com.amyrmo.damavand.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.FlagBadge
import com.amyrmo.damavand.lite.ui.components.MountainContourLine
import com.amyrmo.damavand.lite.ui.components.RefreshLineIcon
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Servers tab (spec §5): choose a profile, see real pings, test all.
 * Real profiles only. Fastest-first sorting only after a test completes —
 * the order is snapshotted when a test starts and never re-sorts mid-test.
 */
@Composable
fun ServersScreen(
    vm: LiteViewModel,
    profiles: List<Profile>,
    selectedId: String?,
    isConnected: Boolean,
    listState: LazyListState
) {
    val isTesting by vm.isTesting.collectAsState()
    val testProgress by vm.testProgress.collectAsState()
    val testingIds by vm.testingIds.collectAsState()
    var showAddSub by remember { mutableStateOf(false) }

    // Freeze the display order while a test runs (spec §5.2).
    var frozenIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(isTesting) {
        if (isTesting) {
            if (frozenIds == null) frozenIds = sortedProfiles(profiles).map { it.id }
        } else {
            frozenIds = null
        }
    }
    val displayList: List<Profile> = remember(profiles, frozenIds, isTesting) {
        val frozen = frozenIds
        if (frozen != null) {
            val byId = profiles.associateBy { it.id }
            val ordered = frozen.mapNotNull { byId[it] }
            ordered + profiles.filter { it.id !in frozen.toSet() }
        } else {
            sortedProfiles(profiles)
        }
    }
    val fastestId = vm.fastestProfile?.id
    val allFailed = profiles.isNotEmpty() && !isTesting &&
        profiles.all { it.lastTestedAt > 0 && it.lastPingMs <= 0 }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.tab_servers),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(16.dp))
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
        if (profiles.isEmpty()) {
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
                            .clickable(onClick = { showAddSub = true })
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    )
                }
            }
        } else {
            items(displayList, key = { it.id }) { profile ->
                ServerRow(
                    profile = profile,
                    isSelected = profile.id == selectedId,
                    isFastest = profile.id == fastestId,
                    isTesting = profile.id in testingIds,
                    onSelect = { vm.selectProfile(profile.id) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddSub) {
        AddSubscriptionDialog(vm = vm, onDismiss = { showAddSub = false })
    }
}

/** Fastest first after a test has run; before any test, subscription order. */
private fun sortedProfiles(list: List<Profile>): List<Profile> {
    if (list.none { it.lastPingMs > 0 }) return list
    return list.sortedWith(
        compareBy({ it.lastPingMs <= 0 }, { it.lastPingMs })
    )
}

// ---- Server row (§5.2) ----

@Composable
private fun ServerRow(
    profile: Profile,
    isSelected: Boolean,
    isFastest: Boolean,
    isTesting: Boolean,
    onSelect: () -> Unit
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
        // Radio 20dp: outer circle; selected = red dot 10dp inside.
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
                    .joinToString("، ")
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
        Spacer(Modifier.width(12.dp))
        // Ping (end column, right-aligned).
        Column(horizontalAlignment = Alignment.End) {
            when {
                isTesting -> {
                    Text(
                        text = "…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = stringResource(R.string.stat_ping),
                        fontSize = 11.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn
                    )
                }
                profile.lastTestedAt == 0L -> {
                    Text(
                        text = "—",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = stringResource(R.string.ping_untested),
                        fontSize = 11.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn
                    )
                }
                profile.lastPingMs < 0 -> {
                    Text(
                        text = stringResource(R.string.ping_failed),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiteColors.error,
                        fontFamily = Vazirmatn
                    )
                    Text(
                        text = stringResource(R.string.stat_ping),
                        fontSize = 11.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn
                    )
                }
                else -> {
                    val color = when {
                        profile.lastPingMs < 150 -> LiteColors.green
                        profile.lastPingMs <= 300 -> LiteColors.textSecondary
                        else -> LiteColors.error
                    }
                    Text(
                        text = LiteFormat.formatPing(profile.lastPingMs),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        fontFamily = Vazirmatn,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = stringResource(R.string.stat_ping),
                        fontSize = 11.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn
                    )
                }
            }
        }
    }
}

// ---- Subscription import (one quiet dialog, spec §8.5) ----

@Composable
fun AddSubscriptionDialog(vm: LiteViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
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
                if (subImport == LiteViewModel.SubImportState.Failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sub_import_failed),
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
