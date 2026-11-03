package com.amyrmo.damavand.ui.servers

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.data.profiles.ProfileFactory
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.ui.components.QrCode

/**
 * Share a profile (§5.2 overflow → share): the canonical link from
 * [ProfileFactory.shareLink], its QR code, copy and system-share actions.
 * Profiles with no link form (raw sing-box documents, chains) say so.
 */
@Composable
fun ShareDialog(
    profile: Profile,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val link = ProfileFactory.shareLink(profile)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (link == null) {
                    Text(
                        text = stringResource(R.string.servers_no_link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    QrCode(content = link, modifier = Modifier.size(220.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = link,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                    )
                }
            }
        },
        confirmButton = {
            if (link != null) {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.common_share))
                }
            }
        },
        dismissButton = {
            if (link != null) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(link))
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.common_copy))
                }
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}
