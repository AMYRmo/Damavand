@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.amyrmo.damavand.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.crypto.DmvLink
import com.amyrmo.damavand.crypto.DmvSealRequest
import com.amyrmo.damavand.crypto.dmvFileName
import com.amyrmo.damavand.data.config.ConfigRepository
import com.amyrmo.damavand.data.config.DmvRelay
import com.amyrmo.damavand.platform.currentTimeMillis
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.*
import com.amyrmo.damavand.ui.components.QrDialog
import com.amyrmo.damavand.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun formatExpiryLeft(
    epochMs: Long,
    expiredStr: String,
    daysHoursTmpl: String,
    daysTmpl: String,
    hoursMinsTmpl: String,
    hoursTmpl: String,
    minsTmpl: String,
): String {
    val diffMs = epochMs - currentTimeMillis()
    if (diffMs <= 0) return expiredStr
    val totalMinutes = diffMs / (60 * 1000L)
    val days = totalMinutes / (24 * 60)
    val remMinutesAfterDays = totalMinutes % (24 * 60)
    val hours = remMinutesAfterDays / 60
    val minutes = remMinutesAfterDays % 60
    return when {
        days > 0 && hours > 0 -> daysHoursTmpl.replace("%1\$d", days.toString()).replace("%2\$d", hours.toString())
        days > 0 -> daysTmpl.replace("%1\$d", days.toString())
        hours > 0 && minutes > 0 -> hoursMinsTmpl.replace("%1\$d", hours.toString()).replace("%2\$d", minutes.toString())
        hours > 0 -> hoursTmpl.replace("%1\$d", hours.toString())
        else -> minsTmpl.replace("%1\$d", minutes.toString())
    }
}

/**
 * Lock & Share sheet (Damavand .dmv flavour of the reference design): seals a
 * profile's share link into a .dmv container — optionally bound to a buyer's
 * device code, with a note, an expiry and an optional relay short link — and
 * offers the result as a damavand:// link, a .dmv file, a share message or a QR code.
 */
@Composable
fun LockShareSheet(
    profileName: String,
    rawPayload: String,
    repository: ConfigRepository,
    onDismiss: () -> Unit,
) {
    val platform = LocalPlatform.current
    val strExpired = stringResource(Res.string.lock_expired)
    val strDaysHours = stringResource(Res.string.lock_expires_days_hours)
    val strDays = stringResource(Res.string.lock_expires_days)
    val strHoursMins = stringResource(Res.string.lock_expires_hours_mins)
    val strHours = stringResource(Res.string.lock_expires_hours)
    val strMins = stringResource(Res.string.lock_expires_mins)
    val toastRelayUrlErr = stringResource(Res.string.lock_err_relay_url)
    val toastRelayUploadErr = stringResource(Res.string.lock_err_relay_upload)
    val toastCreatedExclusive = stringResource(Res.string.lock_toast_created_exclusive)
    val toastCreatedPublic = stringResource(Res.string.lock_toast_created_public)
    val toastGeneratingErr = stringResource(Res.string.lock_err_generating)
    val shareHeader = stringResource(Res.string.lock_share_header)
    val shareName = stringResource(Res.string.lock_share_name)
    val shareValidity = stringResource(Res.string.lock_share_validity)
    val sharePermanent = stringResource(Res.string.lock_share_permanent)
    val shareDeviceExclusive = stringResource(Res.string.lock_share_device_exclusive)
    val shareWarning = stringResource(Res.string.lock_share_warning)
    val sharePublicType = stringResource(Res.string.lock_share_public_type)
    val sharePublicInfo = stringResource(Res.string.lock_share_public_info)
    val shareNote = stringResource(Res.string.lock_share_note)
    val shareLink = stringResource(Res.string.lock_share_link)
    val shareInstructions = stringResource(Res.string.lock_share_instructions)
    val toastCopied = stringResource(Res.string.copied)
    val scope = rememberCoroutineScope()

    val myDeviceCode = remember { repository.dmvDeviceId() }
    val (relayUrl0, relayToken0) = remember { repository.dmvRelaySettings() }

    var configTitle by remember { mutableStateOf(profileName) }
    var userNote by remember { mutableStateOf("") }
    var lockMode by remember { mutableIntStateOf(0) } // 0: device code, 1: public
    var buyerDeviceCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var selectedExpiryOption by remember { mutableIntStateOf(5) } // 0 none, 1 5m, 2 1h, 3 6h, 4 24h, 5 3d, 6 7d, 7 30d, 8 custom
    var customDays by remember { mutableStateOf("0") }
    var customHours by remember { mutableStateOf("0") }
    var customMinutes by remember { mutableStateOf("5") }
    var sizeCapMiB by remember { mutableStateOf("") }

    var useRelay by remember { mutableStateOf(relayUrl0.isNotBlank()) }
    var relayUrlInput by remember { mutableStateOf(relayUrl0) }
    var relayTokenInput by remember { mutableStateOf(relayToken0) }
    var generating by remember { mutableStateOf(false) }

    var generatedLink by remember { mutableStateOf<String?>(null) }
    var generatedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var generatedExpiresAt by remember { mutableStateOf<Long?>(null) }
    var generatedDeviceCode by remember { mutableStateOf<String?>(null) }

    var linkCopied by remember { mutableStateOf(false) }
    var packageCopied by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    val passwordMismatch = password2 != password
    val warnDeviceCode = stringResource(Res.string.lock_warn_enter_device_code)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.lock_share_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(Res.string.lock_share_subtitle), style = MaterialTheme.typography.bodySmall)

            // My device code — buyers send this to a seller to get an exclusive config.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.lock_my_device_title), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(myDeviceCode, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(stringResource(Res.string.lock_my_device_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        platform.copyToClipboard(myDeviceCode)
                        platform.toast(toastCopied)
                    }) {
                        Icon(painter = painterResource(Res.drawable.ic_content_paste), contentDescription = stringResource(Res.string.action_copy), modifier = Modifier.size(18.dp))
                    }
                }
            }

            OutlinedTextField(
                value = configTitle,
                onValueChange = { configTitle = it },
                label = { Text(stringResource(Res.string.lock_config_title)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = userNote,
                onValueChange = { userNote = it },
                label = { Text(stringResource(Res.string.lock_buyer_note)) },
                placeholder = { Text(stringResource(Res.string.lock_buyer_note_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
            )

            // Lock type
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lock_type_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = lockMode == 0,
                        onClick = { lockMode = 0 },
                        label = { Text(stringResource(Res.string.lock_type_device), fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = lockMode == 1,
                        onClick = { lockMode = 1 },
                        label = { Text(stringResource(Res.string.lock_type_public), fontSize = 12.sp) },
                    )
                }

                if (lockMode == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painter = painterResource(Res.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(Res.string.lock_buyer_device_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedTextField(
                            value = buyerDeviceCode,
                            onValueChange = { buyerDeviceCode = it },
                            label = { Text(stringResource(Res.string.lock_buyer_device_label)) },
                            placeholder = { Text(stringResource(Res.string.lock_buyer_device_placeholder)) },
                            supportingText = { Text(stringResource(Res.string.lock_buyer_device_desc), fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = platform.readClipboard()
                                    if (!clip.isNullOrBlank()) buyerDeviceCode = clip.trim()
                                }) {
                                    Icon(painter = painterResource(Res.drawable.ic_content_paste), contentDescription = stringResource(Res.string.lock_paste_device_code), modifier = Modifier.size(18.dp))
                                }
                            },
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painter = painterResource(Res.drawable.ic_public), contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(Res.string.lock_public_desc), fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.lock_password_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password2,
                        onValueChange = { password2 = it },
                        label = { Text(stringResource(Res.string.dmv_password_repeat)) },
                        isError = passwordMismatch,
                        supportingText = if (passwordMismatch) {
                            { Text(stringResource(Res.string.dmv_password_mismatch)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                }
            }

            HorizontalDivider()

            // Expiration
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(Res.drawable.ic_schedule), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.lock_expiration_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val expiryLabels = listOf(
                    stringResource(Res.string.lock_exp_permanent),
                    stringResource(Res.string.lock_exp_5min),
                    stringResource(Res.string.lock_exp_1hour),
                    stringResource(Res.string.lock_exp_6hours),
                    stringResource(Res.string.lock_exp_24hours),
                    stringResource(Res.string.lock_exp_3days),
                    stringResource(Res.string.lock_exp_7days),
                    stringResource(Res.string.lock_exp_30days),
                    stringResource(Res.string.lock_exp_custom),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    expiryLabels.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = selectedExpiryOption == idx,
                            onClick = { selectedExpiryOption = idx },
                            label = { Text(label, fontSize = 11.sp) },
                        )
                    }
                }
                if (selectedExpiryOption == 8) {
                    Text(stringResource(Res.string.lock_exp_custom_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customDays,
                            onValueChange = { customDays = it.filter(Char::isDigit) },
                            label = { Text(stringResource(Res.string.lock_days)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = customHours,
                            onValueChange = { customHours = it.filter(Char::isDigit) },
                            label = { Text(stringResource(Res.string.lock_hours)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter(Char::isDigit) },
                            label = { Text(stringResource(Res.string.lock_minutes)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )
                    }
                }
                OutlinedTextField(
                    value = sizeCapMiB,
                    onValueChange = { sizeCapMiB = it.filter(Char::isDigit) },
                    label = { Text(stringResource(Res.string.dmv_size_cap) + " (MiB)") },
                    supportingText = { Text(stringResource(Res.string.dmv_size_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                )
            }

            HorizontalDivider()

            // Relay (short link)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.lock_relay_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = useRelay, onCheckedChange = { useRelay = it })
                }
                Text(stringResource(Res.string.lock_relay_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (useRelay) {
                    OutlinedTextField(
                        value = relayUrlInput,
                        onValueChange = { relayUrlInput = it },
                        label = { Text(stringResource(Res.string.lock_relay_addr)) },
                        placeholder = { Text("https://dmv.example.workers.dev") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = relayTokenInput,
                        onValueChange = { relayTokenInput = it },
                        label = { Text(stringResource(Res.string.lock_relay_token)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                }
            }

            Button(
                onClick = {
                    val device = buyerDeviceCode.trim()
                    if (lockMode == 0 && device.isEmpty()) {
                        platform.toast(warnDeviceCode)
                        return@Button
                    }
                    if (passwordMismatch) return@Button

                    val now = currentTimeMillis()
                    val expiresAt: Long? = when (selectedExpiryOption) {
                        0 -> null
                        1 -> now + 5 * 60 * 1000L
                        2 -> now + 60 * 60 * 1000L
                        3 -> now + 6 * 3600 * 1000L
                        4 -> now + 24 * 3600 * 1000L
                        5 -> now + 3 * 24 * 3600 * 1000L
                        6 -> now + 7 * 24 * 3600 * 1000L
                        7 -> now + 30 * 24 * 3600 * 1000L
                        8 -> {
                            val totalMs = ((customDays.toLongOrNull() ?: 0) * 24 * 3600L +
                                (customHours.toLongOrNull() ?: 0) * 3600L +
                                (customMinutes.toLongOrNull() ?: 0) * 60L) * 1000L
                            if (totalMs > 0) now + totalMs else null
                        }
                        else -> null
                    }

                    val sealReq = DmvSealRequest(
                        configPayload = rawPayload,
                        name = configTitle,
                        note = userNote,
                        expiresAt = expiresAt,
                        password = if (lockMode == 0) device else password.ifBlank { null },
                        deviceBound = (lockMode == 0),
                        maxSizeBytes = sizeCapMiB.toLongOrNull()?.takeIf { it > 0 }?.let { it * 1024L * 1024L },
                    )

                    if (generating) return@Button
                    generating = true
                    scope.launch {
                        try {
                            // Argon2 (64 MiB) + compression: keep it off the UI thread.
                            val sealedBytes = withContext(Dispatchers.Default) { repository.createLockedDmv(sealReq) }
                            generatedBytes = sealedBytes
                            var link = DmvLink.encode(sealedBytes)
                            if (useRelay) {
                                val hostOk = DmvRelay.hostOf(relayUrlInput) != null
                                if (!hostOk) {
                                    platform.toast(toastRelayUrlErr)
                                } else {
                                    val up = runCatching {
                                        DmvRelay.upload(relayUrlInput, relayTokenInput, sealedBytes, expiresAt, null)
                                    }
                                    up.onSuccess {
                                        link = DmvLink.relayLink(it)
                                        repository.saveDmvRelaySettings(relayUrlInput, relayTokenInput)
                                    }.onFailure {
                                        platform.toast(toastRelayUploadErr.replace("%1\$s", it.message ?: ""))
                                    }
                                }
                            }
                            generatedLink = link
                            generatedExpiresAt = expiresAt
                            generatedDeviceCode = if (lockMode == 0) device else null
                            linkCopied = false
                            packageCopied = false
                            platform.toast(if (lockMode == 0) toastCreatedExclusive else toastCreatedPublic)
                        } catch (e: Throwable) {
                            platform.toast(toastGeneratingErr.replace("%1\$s", e.message ?: ""))
                        } finally {
                            generating = false
                        }
                    }
                },
                enabled = !passwordMismatch,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(painter = painterResource(Res.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (generating) stringResource(Res.string.lock_btn_generating) else stringResource(Res.string.lock_btn_generate), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Result card
            AnimatedVisibility(visible = generatedLink != null) {
                val link = generatedLink ?: ""
                val expiresAt = generatedExpiresAt
                val lockedDevice = generatedDeviceCode ?: ""

                val fullPackageText = buildString {
                    appendLine(shareHeader)
                    appendLine(shareName.replace("%1\$s", configTitle))
                    appendLine(shareValidity.replace("%1\$s", if (expiresAt != null) formatExpiryLeft(expiresAt, strExpired, strDaysHours, strDays, strHoursMins, strHours, strMins) else sharePermanent))
                    if (lockedDevice.isNotEmpty()) {
                        appendLine(shareDeviceExclusive.replace("%1\$s", lockedDevice))
                        appendLine(shareWarning)
                    } else {
                        appendLine(sharePublicType)
                        appendLine(sharePublicInfo)
                    }
                    if (userNote.isNotBlank()) {
                        appendLine(shareNote.replace("%1\$s", userNote))
                    }
                    appendLine()
                    appendLine(shareLink)
                    appendLine(link)
                    appendLine()
                    appendLine(shareInstructions)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.lock_ready), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF34C759))
                            Text(
                                if (expiresAt != null) formatExpiryLeft(expiresAt, strExpired, strDaysHours, strDays, strHoursMins, strHours, strMins) else stringResource(Res.string.lock_exp_permanent),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            "Length: ${link.length}" + if (link.startsWith("damavand://r.")) " (Short)" else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    link,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                )
                                val toastLinkCopied = stringResource(Res.string.lock_link_copied)
                                IconButton(
                                    onClick = {
                                        platform.copyToClipboard(link)
                                        linkCopied = true
                                        platform.toast(toastLinkCopied)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(if (linkCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste),
                                        contentDescription = "Copy Link",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        if (lockedDevice.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.3f)),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(Res.drawable.ic_lock), contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(stringResource(Res.string.lock_locked_for_device), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(lockedDevice, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val toastPackage = stringResource(Res.string.lock_package_copied)
                                Button(
                                    onClick = {
                                        platform.copyToClipboard(fullPackageText)
                                        packageCopied = true
                                        platform.toast(toastPackage)
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(painter = painterResource(if (packageCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste), contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (packageCopied) stringResource(Res.string.copied) else stringResource(Res.string.lock_copy_msg), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val rawBytes = generatedBytes ?: runCatching { DmvLink.decode(link) }.getOrNull()
                                        if (rawBytes != null) {
                                            platform.shareFiles(listOf(dmvFileName(configTitle) to rawBytes))
                                        }
                                    },
                                    modifier = Modifier.height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_description), contentDescription = "Share .dmv file", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(Res.string.lock_send_file), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { platform.shareText(fullPackageText) },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_ios_share), contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.lock_share_text), fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showQrDialog = true },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(painter = painterResource(Res.drawable.ic_qr_code_2), contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.lock_qr_code), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog && generatedLink != null) {
        val link = generatedLink!!
        QrDialog(
            title = configTitle,
            text = link,
            copyLabel = stringResource(Res.string.action_copy),
            shareLabel = stringResource(Res.string.action_share),
            closeLabel = stringResource(Res.string.action_close),
            unsupportedLabel = stringResource(Res.string.qr_too_large),
            onCopy = {
                platform.copyToClipboard(link)
                platform.toast(toastCopied)
            },
            onShare = { platform.shareText(link) },
            onDismiss = { showQrDialog = false },
        )
    }
}
