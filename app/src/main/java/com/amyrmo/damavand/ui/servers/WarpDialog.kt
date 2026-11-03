package com.amyrmo.damavand.ui.servers

// Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the
// owner's own fork lineage of that codebase.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R

/**
 * Cloudflare WARP registration (ported from ZedSecure's WarpDialog).
 *
 * v2 has no WARP/MASQUE backend yet — device registration needs
 * :model/:logic wiring that is outside this UI pass — so, unlike Zed's
 * original, the Register button is DISABLED and the dialog states
 * plainly ([R.string.warp_backend_pending]) that registration arrives
 * with the backend wiring. No fake registration, no network calls.
 */
@Composable
fun WarpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.warp_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.warp_add_body))
                Text(
                    text = stringResource(R.string.warp_add_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.warp_backend_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { /* Disabled until the MASQUE backend lands. */ },
                enabled = false,
            ) {
                Text(stringResource(R.string.warp_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
