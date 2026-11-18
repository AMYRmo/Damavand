package com.amyrmo.damavand.lite.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.BuiltInPsiphon
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.Subscription
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.CircleIconButton
import com.amyrmo.damavand.lite.ui.components.DotsVerticalVector
import com.amyrmo.damavand.lite.ui.components.RefreshVector
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Subscriptions manager (3.1.0, PattNG): every stored subscription
 * with its profile count and last refresh, plus update / rename /
 * auto-update / delete per subscription.
 */
@Composable
fun SubscriptionsScreen(vm: LiteViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val subscriptions by vm.subscriptions.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var menuSub by remember { mutableStateOf<Subscription?>(null) }
    var renameSub by remember { mutableStateOf<Subscription?>(null) }
    var deleteSub by remember { mutableStateOf<Subscription?>(null) }

    // 1.0.1.5: share a subscription's URL through the system sheet,
    // so the user can send a sub to a friend who pastes it into
    // Damavand (or any client) — including the built-in Psiphon
    // link, which seeds the whole group on import.
    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        SubScreenHeader(titleRes = R.string.subs_title, onBack = onBack)
        Spacer(Modifier.height(16.dp))
        if (subscriptions.isEmpty()) {
            Text(
                text = stringResource(R.string.subs_empty),
                fontSize = 14.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(subscriptions, key = { it.url }) { sub ->
                    val count = profiles.count { it.subscriptionUrl == sub.url }
                    SubscriptionRow(
                        sub = sub,
                        profileCount = count,
                        onRefresh = { vm.updateSubscription(sub.url) },
                        onMenu = { menuSub = sub }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    val target = menuSub
    if (target != null) {
        AlertDialog(
            onDismissRequest = { menuSub = null },
            title = {
                Text(
                    text = target.displayName,
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
                    // 1.0.1.5 (BuiltInPsiphon): the built-in group has
                    // nothing to refresh from, no auto-update to
                    // toggle, and cannot be deleted — only rename and
                    // share apply to it.
                    if (!BuiltInPsiphon.isBuiltIn(target.url)) {
                        MenuActionRow(
                            labelRes = R.string.sub_menu_update,
                            onClick = { vm.updateSubscription(target.url); menuSub = null }
                        )
                    }
                    MenuActionRow(
                        labelRes = R.string.sub_menu_rename,
                        onClick = { renameSub = target; menuSub = null }
                    )
                    MenuActionRow(
                        labelRes = R.string.server_menu_share,
                        onClick = { shareText(target.url); menuSub = null }
                    )
                    if (!BuiltInPsiphon.isBuiltIn(target.url)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.sub_auto_update_label),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LiteColors.text,
                                fontFamily = Vazirmatn,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = target.autoUpdate,
                                onCheckedChange = {
                                    vm.setSubscriptionAutoUpdate(target.url, it)
                                    menuSub = null
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = LiteColors.accent,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedThumbColor = LiteColors.text,
                                    uncheckedTrackColor = LiteColors.surface2,
                                    uncheckedBorderColor = LiteColors.hairlineStrong
                                )
                            )
                        }
                    }
                    if (!BuiltInPsiphon.isBuiltIn(target.url)) {
                        MenuActionRow(
                            labelRes = R.string.sub_menu_delete,
                            danger = true,
                            onClick = { deleteSub = target; menuSub = null }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuSub = null }) {
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

    val renaming = renameSub
    if (renaming != null) {
        var name by remember { mutableStateOf(renaming.name) }
        AlertDialog(
            onDismissRequest = { renameSub = null },
            title = {
                Text(
                    text = stringResource(R.string.sub_rename_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.sub_rename_hint),
                            fontSize = 14.sp,
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameSubscription(renaming.url, name)
                        renameSub = null
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
                TextButton(onClick = { renameSub = null }) {
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

    val deleting = deleteSub
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteSub = null },
            title = {
                Text(
                    text = stringResource(R.string.sub_delete_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.sub_delete_body, deleting.displayName),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSubscription(deleting.url)
                        deleteSub = null
                    }
                ) {
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
                TextButton(onClick = { deleteSub = null }) {
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
}

@Composable
private fun SubscriptionRow(
    sub: Subscription,
    profileCount: Int,
    onRefresh: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sub.displayName,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 1.0.1.5 (BuiltInPsiphon): the built-in group has no URL
            // to show — it is not a subscription the user added.
            if (!BuiltInPsiphon.isBuiltIn(sub.url)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub.url,
                    fontSize = 11.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Ltr)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subs_profiles_count, LiteFormat.toPersian(profileCount.toString())) +
                    " — " + formatLastUpdated(sub.lastUpdated),
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn
            )
        }
        Spacer(Modifier.width(8.dp))
        // 1.0.1.5 (BuiltInPsiphon): the built-in group has nothing
        // to refresh from — no refresh button.
        if (!BuiltInPsiphon.isBuiltIn(sub.url)) {
            CircleIconButton(
                buttonSize = 36.dp,
                glyphSize = 16.dp,
                filled = false,
                onClick = onRefresh,
                glyphColor = LiteColors.text,
                icon = RefreshVector,
                contentDescription = stringResource(R.string.cd_refresh)
            )
            Spacer(Modifier.width(8.dp))
        }
        CircleIconButton(
            buttonSize = 36.dp,
            glyphSize = 18.dp,
            filled = false,
            onClick = onMenu,
            glyphColor = LiteColors.text,
            icon = DotsVerticalVector,
            contentDescription = stringResource(R.string.cd_more_options)
        )
    }
}

@Composable
private fun MenuActionRow(labelRes: Int, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) LiteColors.error else LiteColors.text,
            fontFamily = Vazirmatn
        )
    }
}

@Composable
private fun formatLastUpdated(ts: Long): String {
    if (ts <= 0) return stringResource(R.string.subs_updated_never)
    val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(ts))
    return LiteFormat.toPersian(date)
}
