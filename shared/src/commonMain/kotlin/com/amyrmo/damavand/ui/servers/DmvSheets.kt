@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.*

/**
 * Import dialog for a received .dmv config (file, damavand:// link or relay short
 * link): shows the container metadata, the seller note and the lock state, and
 * asks for the password / one-time key when one is needed.
 */
@Composable
fun DmvImportSheet(
    fileName: String,
    configName: String? = null,
    note: String? = null,
    deviceBound: Boolean = false,
    myDeviceCode: String? = null,
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
            if (!configName.isNullOrBlank()) {
                Text(configName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(fileName, style = MaterialTheme.typography.bodySmall)
            if (!note.isNullOrBlank()) {
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
            if (deviceBound) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painter = painterResource(Res.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(Res.string.dmv_device_locked), style = MaterialTheme.typography.bodyMedium)
                }
                if (!myDeviceCode.isNullOrBlank()) {
                    Column {
                        Text(stringResource(Res.string.lock_my_device_title), style = MaterialTheme.typography.labelSmall)
                        Text(myDeviceCode, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
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
                enabled = !expired && (!passwordProtected || password.isNotEmpty() || deviceBound),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.dmv_import_action), fontWeight = FontWeight.SemiBold) }
        }
    }
}
