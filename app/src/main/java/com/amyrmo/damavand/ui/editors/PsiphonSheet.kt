package com.amyrmo.damavand.ui.editors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.model.PsiphonMode
import com.amyrmo.damavand.model.PsiphonProfile
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.theme.accent

/**
 * The Psiphon editor (§5.2): country, mode, and the CDN fronting section
 * (edge IPs + SNI) behind a collapsible — the fields the engine consumes,
 * nothing else.
 */
@Composable
fun PsiphonEditorSheet(
    profile: PsiphonProfile,
    onSave: (PsiphonProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var country by remember { mutableStateOf(profile.country) }
    var mode by remember { mutableStateOf(profile.mode) }
    var cdnIps by remember { mutableStateOf(profile.cdnIps.joinToString("\n")) }
    var cdnSni by remember { mutableStateOf(profile.cdnSni) }
    var cdnExpanded by remember {
        mutableStateOf(profile.cdnIps.isNotEmpty() || profile.cdnSni.isNotBlank())
    }

    EditorSheet(
        title = stringResource(R.string.editor_psiphon),
        accent = profile.kind.accent(),
        saveEnabled = name.isNotBlank(),
        error = if (name.isBlank()) stringResource(R.string.editor_name_required) else null,
        onSave = {
            onSave(
                profile.copy(
                    name = name.trim(),
                    country = country.trim().uppercase(),
                    mode = mode,
                    cdnIps = cdnIps.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    cdnSni = cdnSni.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        FormTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
        )
        FormTextField(
            value = country,
            onValueChange = { country = it },
            label = stringResource(R.string.psi_country),
            placeholder = "US",
            forceLtr = true,
        )
        PickerRow(
            label = stringResource(R.string.psi_mode),
            value = mode,
            options = PsiphonMode.entries,
            labelOf = { it.name.lowercase() },
            onPick = { mode = it },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { cdnExpanded = !cdnExpanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DamavandIcon(
                kind = DamavandIconKind.ChevronRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 18.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.psi_cdn),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        AnimatedVisibility(visible = cdnExpanded) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                FormTextField(
                    value = cdnIps,
                    onValueChange = { cdnIps = it },
                    label = stringResource(R.string.psi_cdn_ips),
                    singleLine = false,
                    minLines = 3,
                    forceLtr = true,
                )
                FormTextField(
                    value = cdnSni,
                    onValueChange = { cdnSni = it },
                    label = stringResource(R.string.psi_cdn_sni),
                    forceLtr = true,
                )
            }
        }
    }
}
