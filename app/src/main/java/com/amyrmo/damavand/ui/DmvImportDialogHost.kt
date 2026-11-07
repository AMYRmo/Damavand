package com.amyrmo.damavand.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.DmvImportBus
import com.amyrmo.damavand.crypto.DmvCrypto
import com.amyrmo.damavand.crypto.DmvExpiredException
import com.amyrmo.damavand.crypto.DmvKeyConsumedException
import com.amyrmo.damavand.crypto.DmvMetadata
import com.amyrmo.damavand.crypto.DmvTooLargeException
import com.amyrmo.damavand.crypto.DmvWrongPasswordException
import com.amyrmo.damavand.data.config.ConfigRepository
import com.amyrmo.damavand.data.config.RelayAlreadyClaimedException
import com.amyrmo.damavand.ui.servers.DmvImportSheet
import kotlinx.coroutines.launch

/**
 * Compose overlay that reacts to incoming .dmv imports (files, damavand:// links and
 * relay short links): downloads relay blobs when needed, peeks metadata, asks
 * for the password / one-time key and imports through the repository's
 * locked-import flow (device check + re-seal).
 */
@Composable
fun DmvImportDialogHost(repository: ConfigRepository) {
    val pending by DmvImportBus.pending.collectAsStateWithLifecycle()
    val request = pending ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val errWrongPassword = stringResource(R.string.dmv_wrong_password)
    val errWrongDevice = stringResource(R.string.dmv_wrong_device)
    val errExpired = stringResource(R.string.dmv_expired)
    val errTooLarge = stringResource(R.string.dmv_too_large)
    val errBadFile = stringResource(R.string.dmv_bad_file)
    val errKeyConsumed = stringResource(R.string.dmv_key_consumed)
    val importedMsg = stringResource(R.string.dmv_imported)

    var error by remember(request) { mutableStateOf<String?>(null) }
    var busy by remember(request) { mutableStateOf(false) }
    var bytes by remember(request) { mutableStateOf(request.bytes) }
    var meta by remember(request) {
        mutableStateOf(request.bytes?.let { runCatching { DmvCrypto.peek(it) }.getOrNull() })
    }

    fun fail(e: Throwable?, m: DmvMetadata?) {
        error = when {
            e is DmvExpiredException -> errExpired
            e is DmvTooLargeException -> errTooLarge
            e is DmvKeyConsumedException || e is RelayAlreadyClaimedException -> errKeyConsumed
            e is DmvWrongPasswordException && m?.deviceBound == true -> errWrongDevice
            e is DmvWrongPasswordException -> errWrongPassword
            else -> errBadFile
        }
    }

    val relay = request.relay
    if (bytes == null && relay == null) {
        DmvImportSheet(
            fileName = request.fileName,
            passwordProtected = false,
            expired = false,
            error = errBadFile,
            onDismiss = { DmvImportBus.consume() },
            onImport = { DmvImportBus.consume() },
        )
        return
    }

    val m = meta
    DmvImportSheet(
        fileName = if (relay != null && bytes == null) "damavand://r.${relay.id}@${relay.host}" else request.fileName,
        configName = m?.name,
        note = m?.note,
        deviceBound = m?.deviceBound == true,
        myDeviceCode = if (m?.deviceBound == true) repository.dmvDeviceId() else null,
        // Relay links ask for the (optional) claim key up-front; containers ask when protected.
        passwordProtected = if (bytes == null) true else (m?.passwordProtected == true),
        expired = m?.isExpired == true,
        error = error ?: if (bytes != null && m == null) errBadFile else null,
        onDismiss = { DmvImportBus.consume() },
        onImport = { password ->
            if (busy) return@DmvImportSheet
            val key = password.ifBlank { null }
            val currentBytes = bytes
            if (currentBytes == null && relay != null) {
                // Phase 1: download the sealed blob from the relay, then show its metadata.
                busy = true
                scope.launch {
                    runCatching { repository.fetchDmvRelay(relay, key) }
                        .onSuccess { blob ->
                            bytes = blob
                            meta = runCatching { DmvCrypto.peek(blob) }.getOrNull()
                            if (meta == null) error = errBadFile
                            // No password on the container itself: import right away.
                            if (meta?.passwordProtected == false && meta?.deviceBound == false) {
                                repository.importLockedDmv(blob, key)
                                    .onSuccess {
                                        android.widget.Toast.makeText(context, importedMsg, android.widget.Toast.LENGTH_SHORT).show()
                                        DmvImportBus.consume()
                                    }
                                    .onFailure { fail(it, meta) }
                            }
                        }
                        .onFailure { fail(it, null) }
                    busy = false
                }
                return@DmvImportSheet
            }
            if (currentBytes == null) return@DmvImportSheet
            repository.importLockedDmv(currentBytes, key)
                .onSuccess {
                    android.widget.Toast.makeText(context, importedMsg, android.widget.Toast.LENGTH_SHORT).show()
                    DmvImportBus.consume()
                }
                .onFailure { fail(it, m) }
        },
    )
}
