package com.amyrmo.damavand.crypto

import com.amyrmo.damavand.platform.currentTimeMillis

/**
 * Metadata of a sealed .dmv container, readable without the password
 * (the outer layer is only wrapped with the app key).
 */
data class DmvMetadata(
    val passwordProtected: Boolean,
    val name: String,
    val note: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val deviceBound: Boolean = false,
    val maxSizeBytes: Long? = null,
) {
    val isExpired: Boolean
        get() = expiresAt != null && currentTimeMillis() > expiresAt
}

data class DmvSealRequest(
    val configPayload: String,
    val name: String,
    val note: String,
    val expiresAt: Long?,
    val password: String?,
    /** The password already includes the buyer's device code; stored as a flag so the app knows to add its own. */
    val deviceBound: Boolean = false,
    /** Damavand extra over the reference design: refuse payloads above this size on open. */
    val maxSizeBytes: Long? = null,
)

sealed class DmvException(message: String) : Exception(message)
class DmvVersionException : DmvException("Unsupported .dmv version")
class DmvTamperException : DmvException("File is corrupt or has been tampered with")
class DmvWrongPasswordException : DmvException("Incorrect password")
class DmvExpiredException : DmvException("This locked config has expired")
class DmvPasswordRequiredException : DmvException("A password is required")
class DmvLegacyException : DmvException("This locked config was made by an older version of Damavand")
class DmvTooLargeException : DmvException("Payload exceeds the declared size limit")
class DmvKeyConsumedException(message: String = "This one-time private key has already been consumed") : DmvException(message)

fun dmvFileName(name: String): String {
    val base = name.trim().ifBlank { "config" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
    return if (base.endsWith(".dmv")) base else "$base.dmv"
}
