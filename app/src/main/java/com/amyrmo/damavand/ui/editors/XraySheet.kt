package com.amyrmo.damavand.ui.editors

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.amyrmo.damavand.R
import com.amyrmo.damavand.model.ConfigParser
import com.amyrmo.damavand.model.SingBoxProfile
import com.amyrmo.damavand.model.XrayPayload
import com.amyrmo.damavand.model.XrayProfile
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.theme.accent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val strictJson = Json { ignoreUnknownKeys = true }

private fun parsesAsJsonObject(text: String): Boolean =
    runCatching { strictJson.parseToJsonElement(text) is JsonObject }.getOrDefault(false)

/**
 * The Xray editor (§5.2): remarks, the payload in link or raw-JSON mode
 * (a sealed payload is shown read-only — it round-trips untouched), and
 * the per-profile DNS override. Round-trips through [XrayProfile] only.
 */
@Composable
fun XrayEditorSheet(
    profile: XrayProfile,
    onSave: (XrayProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var mode by remember {
        mutableIntStateOf(if (profile.payload is XrayPayload.RawJson) 1 else 0)
    }
    var link by remember {
        mutableStateOf((profile.payload as? XrayPayload.Link)?.link ?: "")
    }
    var json by remember {
        mutableStateOf((profile.payload as? XrayPayload.RawJson)?.json ?: "")
    }
    var dns by remember { mutableStateOf(profile.dns) }
    val sealed = profile.payload as? XrayPayload.Sealed

    val linkParsed = remember(link) {
        if (link.isBlank()) null else runCatching { ConfigParser.parse(link.trim()) }.getOrNull()
    }
    val linkError = sealed == null && mode == 0 && link.isNotBlank() && linkParsed == null
    val jsonError = sealed == null && mode == 1 && json.isNotBlank() && !parsesAsJsonObject(json)
    val payloadValid = when {
        sealed != null -> true
        mode == 0 -> linkParsed != null
        else -> json.isNotBlank() && parsesAsJsonObject(json)
    }
    val effectiveName = name.trim().ifBlank { linkParsed?.remark?.trim().orEmpty() }
    val error = when {
        linkError -> stringResource(R.string.xray_link_invalid)
        jsonError -> stringResource(R.string.xray_json_invalid)
        payloadValid && effectiveName.isBlank() -> stringResource(R.string.editor_name_required)
        else -> null
    }

    EditorSheet(
        title = stringResource(R.string.editor_xray),
        accent = profile.kind.accent(),
        saveEnabled = payloadValid && effectiveName.isNotBlank(),
        error = error,
        onSave = {
            val payload = when {
                sealed != null -> sealed
                mode == 0 -> XrayPayload.Link(link.trim())
                else -> XrayPayload.RawJson(json.trim())
            }
            onSave(profile.copy(name = effectiveName, payload = payload, dns = dns.trim()))
        },
        onDismiss = onDismiss,
    ) {
        FormTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
            placeholder = linkParsed?.remark?.takeIf { it.isNotBlank() },
        )
        if (sealed != null) {
            Text(
                text = stringResource(R.string.xray_sealed_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(
                value = sealed.zsx,
                onValueChange = {},
                label = stringResource(R.string.xray_payload),
                singleLine = false,
                minLines = 3,
                forceLtr = true,
            )
        } else {
            SegmentedChoice(
                options = listOf(
                    stringResource(R.string.xray_mode_link),
                    stringResource(R.string.xray_mode_json),
                ),
                selectedIndex = mode,
                onSelect = { mode = it },
            )
            if (mode == 0) {
                FormTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = stringResource(R.string.xray_link),
                    singleLine = false,
                    minLines = 3,
                    forceLtr = true,
                    isError = linkError,
                )
            } else {
                FormTextField(
                    value = json,
                    onValueChange = { json = it },
                    label = stringResource(R.string.xray_json_label),
                    singleLine = false,
                    minLines = 6,
                    forceLtr = true,
                    isError = jsonError,
                )
            }
        }
        FormTextField(
            value = dns,
            onValueChange = { dns = it },
            label = stringResource(R.string.common_dns),
            forceLtr = true,
        )
    }
}

/**
 * The sing-box editor (§5.2): remarks, the full (or outbound-fragment)
 * JSON validated as a JSON object before save, fragment toggle.
 */
@Composable
fun SingBoxEditorSheet(
    profile: SingBoxProfile,
    onSave: (SingBoxProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var json by remember { mutableStateOf(profile.json) }
    var fragment by remember { mutableStateOf(profile.fragment) }
    val jsonValid = json.isNotBlank() && parsesAsJsonObject(json)
    val error = when {
        json.isNotBlank() && !jsonValid -> stringResource(R.string.xray_json_invalid)
        jsonValid && name.isBlank() -> stringResource(R.string.editor_name_required)
        else -> null
    }
    EditorSheet(
        title = stringResource(R.string.editor_singbox),
        accent = profile.kind.accent(),
        saveEnabled = jsonValid && name.isNotBlank(),
        error = error,
        onSave = {
            onSave(profile.copy(name = name.trim(), json = json.trim(), fragment = fragment))
        },
        onDismiss = onDismiss,
    ) {
        FormTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
        )
        FormTextField(
            value = json,
            onValueChange = { json = it },
            label = stringResource(R.string.singbox_json_label),
            singleLine = false,
            minLines = 8,
            forceLtr = true,
            isError = json.isNotBlank() && !jsonValid,
        )
        SwitchRow(
            label = stringResource(R.string.singbox_fragment),
            checked = fragment,
            onCheckedChange = { fragment = it },
        )
        if (profile.sourceLink.isNotBlank()) {
            FormTextField(
                value = profile.sourceLink,
                onValueChange = {},
                label = stringResource(R.string.xray_link),
                singleLine = false,
                minLines = 2,
                forceLtr = true,
            )
        }
    }
}
