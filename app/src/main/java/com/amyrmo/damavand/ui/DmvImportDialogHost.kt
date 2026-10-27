package com.amyrmo.damavand.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.DmvImportBus
import com.amyrmo.damavand.crypto.DmvCrypto
import com.amyrmo.damavand.data.config.ConfigRepository
import com.amyrmo.damavand.ui.servers.DmvImportSheet

/**
 * Compose overlay that reacts to incoming .dmv intents (SEND / VIEW):
 * peeks metadata, enforces expiry, asks for the password and imports.
 */
@Composable
fun DmvImportDialogHost(repository: ConfigRepository) {
    val pending by DmvImportBus.pending.collectAsStateWithLifecycle()
    val request = pending ?: return

    var error by remember(request) { mutableStateOf<String?>(null) }
    var meta by remember(request) {
        mutableStateOf(
            runCatching { DmvCrypto.peek(request.bytes) }.getOrNull(),
        )
    }

    val m = meta
    if (m == null) {
        // Invalid file: show a minimal AlertDialog-style message via the sheet with error state
        DmvImportSheet(
            fileName = request.fileName,
            passwordProtected = false,
            expired = false,
            error = stringResource(R.string.dmv_bad_file),
            onDismiss = { DmvImportBus.consume() },
            onImport = { DmvImportBus.consume() },
        )
        return
    }

    DmvImportSheet(
        fileName = request.fileName,
        passwordProtected = m.passwordProtected,
        expired = m.isExpired,
        error = error,
        onDismiss = { DmvImportBus.consume() },
        onImport = { password ->
            val result = runCatching {
                val payload = DmvCrypto.open(request.bytes, password)
                repository.importText(payload)
            }
            result.fold(
                onSuccess = { count ->
                    DmvImportBus.consume()
                    if (count > 0) {
                        // success — the repository toast flow already notifies
                    } else {
                        error = null
                    }
                },
                onFailure = { e ->
                    error = when (e) {
                        is DmvCrypto.DmvWrongPassword -> stringResource(R.string.dmv_wrong_password)
                        is DmvCrypto.DmvExpired -> stringResource(R.string.dmv_expired)
                        is DmvCrypto.DmvTooLarge -> stringResource(R.string.dmv_too_large)
                        else -> stringResource(R.string.dmv_bad_file)
                    }
                },
            )
        },
    )
}
