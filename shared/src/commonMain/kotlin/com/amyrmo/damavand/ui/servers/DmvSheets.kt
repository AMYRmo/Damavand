@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.*
import com.amyrmo.damavand.ui.components.PickerField

/**
 * Share-as-.dmv dialog: pick a password, an expiry window and a payload size cap,
 * then hand the sealed bytes to [onShare].
 */
@Composable
fun DmvShareSheet(
    profileName: String,
    onDismiss: () -> Unit,
    onShare: (password: String, expiresAtHours: Int?, maxSizeKiB: Int?) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("24") }   // hours; blank = never
    var sizeCap by remember { mutableStateOf("1") }   // MiB; blank = unlimited
    var sizeUnit by remember { mutableStateOf("MiB") }

    val expiryMismatch = password2 != password

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
                stringResource(Res.string.dmv_share_title, profileName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(Res.string.dmv_share_intro), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.dmv_password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password2,
                onValueChange = { password2 = it },
                label = { Text(stringResource(Res.string.dmv_password_repeat)) },
                isError = expiryMismatch,
                supportingText = if (expiryMismatch) {
                    { Text(stringResource(Res.string.dmv_password_mismatch)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PickerField(
                label = stringResource(Res.string.dmv_expiry),
                options = listOf(
                    "1" to stringResource(Res.string.dmv_expiry_1h),
                    "6" to stringResource(Res.string.dmv_expiry_6h),
                    "24" to stringResource(Res.string.dmv_expiry_24h),
                    "72" to stringResource(Res.string.dmv_expiry_3d),
                    "168" to stringResource(Res.string.dmv_expiry_7d),
                    "" to stringResource(Res.string.dmv_expiry_never),
                ),
                selected = expiry,
                onSelect = { expiry = it },
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = sizeCap,
                    onValueChange = { sizeCap = it.filter(Char::isDigit) },
                    label = { Text(stringResource(Res.string.dmv_size_cap)) },
                    supportingText = { Text(stringResource(Res.string.dmv_size_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = " ",
                    options = listOf("MiB" to "MB", "GiB" to "GB"),
                    selected = sizeUnit,
                    onSelect = { sizeUnit = it },
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = {
                    val raw = sizeCap.toLongOrNull()?.takeIf { it > 0 } ?: 0L
                    val bytes = when (sizeUnit) {
                        "GiB" -> raw * 1024L * 1024L * 1024L
                        else -> raw * 1024L * 1024L
                    }
                    onShare(
                        password,
                        expiry.toIntOrNull()?.takeIf { it > 0 },
                        bytes.takeIf { it > 0L }?.let { kb -> (kb / 1024L).toInt().coerceAtLeast(1) },
                    )
                },
                enabled = !expiryMismatch,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.dmv_share_create), fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Import dialog for a received .dmv file: shows metadata, asks for the password. */
@Composable
fun DmvImportSheet(
    fileName: String,
    passwordProtected: Boolean,
    expired: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

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
                stringResource(Res.string.dmv_import_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(fileName, style = MaterialTheme.typography.bodySmall)
            if (expired) {
                Text(stringResource(Res.string.dmv_expired), color = MaterialTheme.colorScheme.error)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (passwordProtected) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.dmv_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { onImport(password) },
                enabled = !expired && (!passwordProtected || password.isNotEmpty()),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.dmv_import_action), fontWeight = FontWeight.SemiBold) }
        }
    }
}
