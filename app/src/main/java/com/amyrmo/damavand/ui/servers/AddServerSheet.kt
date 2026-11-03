package com.amyrmo.damavand.ui.servers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.SectionHeader
import com.amyrmo.damavand.ui.theme.accent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BLANK_KINDS = listOf(
    CoreKind.XRAY,
    CoreKind.SINGBOX,
    CoreKind.PSIPHON,
    CoreKind.AETHER,
    CoreKind.MIHOMO,
    CoreKind.WHITEDNS,
)

/**
 * Add-a-server (§5.2): paste a link (parsed by the :model cascade in
 * [com.amyrmo.damavand.data.profiles.ProfileFactory.fromText]), import a
 * file with the same cascade, add a subscription by URL, start a
 * blank per-core editor, or open the Cloudflare WARP entry (free, as
 * in ZedSecure's Add sheet; registration itself is backend-pending —
 * see [WarpDialog]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddServerSheet(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onBlank: (CoreKind) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paste by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf(false) }
    var subUrl by remember { mutableStateOf("") }
    var subName by remember { mutableStateOf("") }
    var subError by remember { mutableStateOf(false) }
    var showWarp by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                }.getOrNull()
            }
            if (text != null && vm.importText(text)) {
                onDismiss()
            } else {
                pasteError = true
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.servers_add),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SectionHeader(title = stringResource(R.string.add_paste))
            FormTextField(
                value = paste,
                onValueChange = {
                    paste = it
                    pasteError = false
                },
                label = stringResource(R.string.add_paste),
                singleLine = false,
                minLines = 3,
                forceLtr = true,
                isError = pasteError,
            )
            if (pasteError) {
                Text(
                    text = stringResource(R.string.add_invalid_link),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    if (vm.importText(paste)) onDismiss() else pasteError = true
                },
                enabled = paste.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_import))
            }
            OutlinedButton(
                onClick = { fileLauncher.launch(arrayOf("text/*", "application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_import_file))
            }

            SectionHeader(title = stringResource(R.string.add_subscription))
            FormTextField(
                value = subUrl,
                onValueChange = {
                    subUrl = it
                    subError = false
                },
                label = stringResource(R.string.sub_url),
                forceLtr = true,
                isError = subError,
            )
            FormTextField(
                value = subName,
                onValueChange = { subName = it },
                label = stringResource(R.string.common_name),
            )
            Button(
                onClick = {
                    scope.launch {
                        val ok = vm.addSubscription(subUrl.trim(), subName.trim().ifBlank { null })
                        if (ok) onDismiss() else subError = true
                    }
                },
                enabled = subUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_subscription))
            }

            SectionHeader(title = stringResource(R.string.add_blank))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BLANK_KINDS.forEach { kind ->
                    OutlinedButton(onClick = { onBlank(kind) }) {
                        Text(
                            text = kind.name.lowercase(),
                            color = kind.accent(),
                        )
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.warp_add_title))
            OutlinedButton(
                onClick = { showWarp = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.warp_add_title))
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Free",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showWarp) {
        WarpDialog(onDismiss = { showWarp = false })
    }
}
