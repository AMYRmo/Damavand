package com.amyrmo.damavand.ui.editors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.amyrmo.damavand.R
import com.amyrmo.damavand.logic.MihomoConfigEditor
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.LiveLogPanel
import com.amyrmo.damavand.ui.components.SectionHeader
import com.amyrmo.damavand.ui.theme.accent

private val LOG_LEVELS = listOf("silent", "error", "warning", "info", "debug")

/**
 * The Mihomo editor (§5.2): YAML text or a subscription URL (both kept —
 * the segmented control only chooses which editor is in front, so
 * switching never destroys a payload), log level, DNS. Save is gated by
 * :logic's own parse: [MihomoConfigEditor.hasDialableProxy] parses the
 * YAML and requires at least one dialable proxy.
 */
@Composable
fun MihomoEditorSheet(
    profile: MihomoProfile,
    onSave: (MihomoProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var yaml by remember { mutableStateOf(profile.yaml) }
    var subUrl by remember { mutableStateOf(profile.subUrl) }
    var logLevel by remember { mutableStateOf(profile.logLevel) }
    var dns by remember { mutableStateOf(profile.dns) }
    var mode by remember {
        mutableStateOf(
            if (profile.yaml.isBlank() && profile.subUrl.isNotBlank()) 1 else 0,
        )
    }

    val yamlValid = yaml.isBlank() || MihomoConfigEditor.hasDialableProxy(yaml)
    val contentValid = (yaml.isNotBlank() && yamlValid) || subUrl.isNotBlank()
    val error = when {
        yaml.isNotBlank() && !yamlValid -> stringResource(R.string.mihomo_invalid)
        contentValid && name.isBlank() -> stringResource(R.string.editor_name_required)
        else -> null
    }

    EditorSheet(
        title = stringResource(R.string.editor_mihomo),
        accent = profile.kind.accent(),
        saveEnabled = contentValid && name.isNotBlank(),
        error = error,
        onSave = {
            onSave(
                profile.copy(
                    name = name.trim(),
                    yaml = yaml,
                    subUrl = subUrl.trim(),
                    logLevel = logLevel,
                    dns = dns.trim(),
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
        SegmentedChoice(
            options = listOf(
                stringResource(R.string.mihomo_mode_yaml),
                stringResource(R.string.mihomo_mode_sub),
            ),
            selectedIndex = mode,
            onSelect = { mode = it },
        )
        if (mode == 0) {
            FormTextField(
                value = yaml,
                onValueChange = { yaml = it },
                label = stringResource(R.string.mihomo_yaml),
                singleLine = false,
                minLines = 8,
                forceLtr = true,
                isError = yaml.isNotBlank() && !yamlValid,
            )
        } else {
            FormTextField(
                value = subUrl,
                onValueChange = { subUrl = it },
                label = stringResource(R.string.mihomo_sub_url),
                forceLtr = true,
            )
        }
        PickerRow(
            label = stringResource(R.string.mihomo_log_level),
            value = logLevel,
            options = LOG_LEVELS,
            labelOf = { it },
            onPick = { logLevel = it },
        )
        FormTextField(
            value = dns,
            onValueChange = { dns = it },
            label = stringResource(R.string.common_dns),
            forceLtr = true,
        )
        SectionHeader(title = stringResource(R.string.live_log))
        LiveLogPanel(filter = "mihomo", height = 140.dp)
    }
}
