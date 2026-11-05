package com.amyrmo.damavand.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.core.VpnManager
import com.amyrmo.damavand.data.net.WarpMasque
import com.amyrmo.damavand.data.net.WarpWireGuard
import com.amyrmo.damavand.domain.config.ShareLink
import com.amyrmo.damavand.domain.model.ConnectionState
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.action_cancel
import com.amyrmo.damavand.shared.resources.action_retry
import com.amyrmo.damavand.shared.resources.warp_add_action
import com.amyrmo.damavand.shared.resources.warp_add_body
import com.amyrmo.damavand.shared.resources.warp_add_body_wg
import com.amyrmo.damavand.shared.resources.warp_add_terms
import com.amyrmo.damavand.shared.resources.warp_mode_masque
import com.amyrmo.damavand.shared.resources.warp_mode_wireguard
import com.amyrmo.damavand.shared.resources.warp_add_title
import com.amyrmo.damavand.shared.resources.warp_failed
import com.amyrmo.damavand.shared.resources.warp_registering
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun WarpDialog(onDismiss: () -> Unit, onRegistered: (link: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // BPB port, cluster 4: WARP can enroll as MASQUE (existing) or as a
    // plain WireGuard device (new) — same dialog, two registrations.
    var wireGuard by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.warp_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (wireGuard) Res.string.warp_add_body_wg else Res.string.warp_add_body))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(selected = !wireGuard, enabled = !busy) { wireGuard = false },
                ) {
                    RadioButton(selected = !wireGuard, onClick = { wireGuard = false }, enabled = !busy)
                    Text(stringResource(Res.string.warp_mode_masque))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(selected = wireGuard, enabled = !busy) { wireGuard = true },
                ) {
                    RadioButton(selected = wireGuard, onClick = { wireGuard = true }, enabled = !busy)
                    Text(stringResource(Res.string.warp_mode_wireguard))
                }
                Text(
                    stringResource(Res.string.warp_add_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(Res.string.warp_registering))
                    }
                }
                error?.let {
                    Text(
                        stringResource(Res.string.warp_failed, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val port = VpnManager.activeSocksPort
                            .takeIf { VpnManager.status.value.state == ConnectionState.Connected }
                        runCatching { if (wireGuard) WarpWireGuard.register(port) else WarpMasque.register(port) }
                            .onSuccess { busy = false; onRegistered(ShareLink.build(it)) }
                            .onFailure { busy = false; error = it.message ?: it.toString() }
                    }
                },
            ) { Text(stringResource(if (error == null) Res.string.warp_add_action else Res.string.action_retry)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
