package com.amyrmo.damavand.ui.servers

// Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the
// owner's own fork lineage of that codebase.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.editors.PickerRow
import com.amyrmo.damavand.ui.editors.SwitchRow

/** MASQUE HTTP versions, mirroring ZedSecure's MASQUE_ALPNS. */
private val MASQUE_HTTP_VERSIONS = listOf("h3", "h2")

/**
 * The MASQUE editor section, ported faithfully from ZedSecure's
 * ManualConfigSheet (Protocol.MASQUE branch): the WARP toggle and its
 * hint, the device/endpoint key fields shown while WARP is on, the
 * HTTP-version picker, and the path / authority / headers / DNS /
 * extra-settings fields.
 *
 * This is the ported UI surface only: every value arrives as a
 * (value, onChange) pair so any editor can own the state, and it has
 * no call site yet. The header note is honest about the current
 * wiring — in Damavand, MASQUE links run through the Aether core.
 */
@Composable
fun MasqueFields(
    warpEnabled: Boolean,
    onWarpChange: (Boolean) -> Unit,
    devicePrivateKey: String,
    onDevicePrivateKeyChange: (String) -> Unit,
    endpointPublicKey: String,
    onEndpointPublicKeyChange: (String) -> Unit,
    httpVersion: String,
    onHttpVersionChange: (String) -> Unit,
    path: String,
    onPathChange: (String) -> Unit,
    authority: String,
    onAuthorityChange: (String) -> Unit,
    headers: String,
    onHeadersChange: (String) -> Unit,
    dns: String,
    onDnsChange: (String) -> Unit,
    extra: String,
    onExtraChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.masque_surface_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            label = stringResource(R.string.manual_masque_warp),
            checked = warpEnabled,
            onCheckedChange = onWarpChange,
        )
        if (warpEnabled) {
            Text(
                text = stringResource(R.string.manual_masque_warp_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(
                value = devicePrivateKey,
                onValueChange = onDevicePrivateKeyChange,
                label = stringResource(R.string.manual_masque_warp_private_key),
                forceLtr = true,
            )
            FormTextField(
                value = endpointPublicKey,
                onValueChange = onEndpointPublicKeyChange,
                label = stringResource(R.string.manual_masque_warp_public_key),
                forceLtr = true,
            )
        }
        PickerRow(
            label = stringResource(R.string.manual_masque_http),
            value = if (httpVersion.trim().lowercase() == "h2") "h2" else "h3",
            options = MASQUE_HTTP_VERSIONS,
            labelOf = { version -> if (version == "h2") "HTTP/2" else "HTTP/3" },
            onPick = onHttpVersionChange,
        )
        FormTextField(
            value = path,
            onValueChange = onPathChange,
            label = stringResource(R.string.manual_masque_path),
            forceLtr = true,
        )
        FormTextField(
            value = authority,
            onValueChange = onAuthorityChange,
            label = stringResource(R.string.manual_masque_authority),
            forceLtr = true,
        )
        FormTextField(
            value = headers,
            onValueChange = onHeadersChange,
            label = stringResource(R.string.manual_masque_headers),
            forceLtr = true,
        )
        FormTextField(
            value = dns,
            onValueChange = onDnsChange,
            label = stringResource(R.string.manual_masque_dns),
            forceLtr = true,
        )
        FormTextField(
            value = extra,
            onValueChange = onExtraChange,
            label = stringResource(R.string.manual_masque_extra),
            singleLine = false,
            minLines = 3,
            forceLtr = true,
        )
    }
}
