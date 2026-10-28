package com.amyrmo.damavand.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
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
import com.amyrmo.damavand.domain.config.MihomoProfile

/** Paste-a-Clash-YAML sheet for the mihomo (Clash.Meta) engine. */
@Composable
fun MihomoSheet(
    onDismiss: () -> Unit,
    onSave: (MihomoProfile) -> Unit,
    initial: MihomoProfile? = null,
) {
    var remarks by remember { mutableStateOf(initial?.remarks ?: "") }
    var yaml by remember { mutableStateOf(initial?.yaml ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.mihomo_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text(stringResource(Res.string.aether_remarks)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = yaml,
                onValueChange = { yaml = it },
                label = { Text(stringResource(Res.string.mihomo_yaml_hint)) },
                placeholder = { Text("proxies:\n  - name: \"...\"", maxLines = 6) },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        MihomoProfile(
                            remarks = remarks.trim(),
                            yaml = yaml.trim().trimStart('-'),
                        ),
                    )
                },
                enabled = MihomoProfile.looksLikeMihomo(yaml),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}
