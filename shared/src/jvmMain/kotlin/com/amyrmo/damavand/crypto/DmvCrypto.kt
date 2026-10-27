package com.amyrmo.damavand.crypto

import com.amyrmo.damavand.platform.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The .dmv sharing format: an encrypted config bundle with an optional password,
 * a size limit and an expiry. Same crypto shape as .zsx (AES-256-GCM over an
 * inner JSON envelope; Argon2id when a password is set) but with its own magic
 * and header fields:
 *
 *   [DMV1][version=1][flags:1][nonce:12][cipher...]
 *
 * flags bit0: password set. The inner envelope carries the config text, the
 * profile name, max-size and expiry so the receiver can validate both.
 */
@OptIn(ExperimentalEncodingApi::class)
object DmvCrypto {
    private val MAGIC = "DMV1".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_LEN = 32
    private const val AAD = "dmv-share-v1"

    private const val ARGON_MEM_KIB = 65_536
    private const val ARGON_ITERATIONS = 3
    private const val ARGON_PARALLELISM = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val rng = SecureRandom()

    @Serializable
    data class Envelope(
        val name: String,
        val payload: String,
        val createdAt: Long,
        val expiresAt: Long? = null,
        val maxSizeBytes: Long? = null,
        val kdf: DmvKdf? = null,
        val nonce: String,
        val cipher: String,
    )

    @Serializable
    data class DmvKdf(val salt: String, val memKiB: Int, val iterations: Int, val parallelism: Int)

    data class Metadata(
        val name: String,
        val createdAt: Long,
        val expiresAt: Long?,
        val maxSizeBytes: Long?,
        val passwordProtected: Boolean,
    ) {
        val isExpired: Boolean get() = expiresAt != null && currentTimeMillis() > expiresAt
    }

    class DmvException(message: String) : Exception(message)
    class DmvWrongPassword : DmvException("Incorrect password")
    class DmvPasswordRequired : DmvException("A password is required")
    class DmvExpired : DmvException("This .dmv file has expired")
    class DmvTampered : DmvException("This .dmv file is corrupt")
    class DmvTooLarge : DmvException("Payload exceeds the declared size limit")

    /** Seal [payload] into .dmv bytes. [password] blank = no encryption password. */
    fun seal(
        payload: String,
        name: String,
        password: String,
        expiresAt: Long?,
        maxSizeBytes: Long?,
    ): ByteArray {
        if (maxSizeBytes != null && payload.toByteArray(Charsets.UTF_8).size > maxSizeBytes) {
            throw DmvTooLarge()
        }
        val body: ByteArray
        val flags: Byte
        if (password.isEmpty()) {
            flags = 0
            body = payload.toByteArray(Charsets.UTF_8)
        } else {
            flags = 1
            val salt = ByteArray(16).also(rng::nextBytes)
            val key = argon2(password, salt)
            val nonce = ByteArray(NONCE_LEN).also(rng::nextBytes)
            val cipher = aesGcm(key, nonce, AAD.toByteArray(), payload.toByteArray(Charsets.UTF_8), encrypt = true)
            body = nonce + cipher
            val kdf = DmvKdf(Base64.encode(salt), ARGON_MEM_KIB, ARGON_ITERATIONS, ARGON_PARALLELISM)
            return header(flags, envelopeJson(name, body, expiresAt, maxSizeBytes, kdf))
        }
        return header(flags, envelopeJson(name, body, expiresAt, maxSizeBytes, null))
    }

    /** Peek metadata without the password. */
    fun peek(bytes: ByteArray): Metadata {
        val (flags, inner) = readOuter(bytes)
        return Metadata(
            name = inner.name,
            createdAt = inner.createdAt,
            expiresAt = inner.expiresAt,
            maxSizeBytes = inner.maxSizeBytes,
            passwordProtected = flags.toInt() and 1 == 1,
        )
    }

    /** Open the payload; enforces expiry + size limit. */
    fun open(bytes: ByteArray, password: String): String {
        val (flags, inner) = readOuter(bytes)
        if (inner.expiresAt != null && currentTimeMillis() > inner.expiresAt) throw DmvExpired()
        val payloadBytes: ByteArray = if (flags.toInt() and 1 == 1) {
            if (password.isEmpty()) throw DmvPasswordRequired()
            val kdf = inner.kdf ?: throw DmvTampered()
            val key = argon2(password, Base64.decode(kdf.salt))
            val nonce = Base64.decode(inner.nonce)
            val cipher = Base64.decode(inner.cipher)
            try {
                aesGcm(key, nonce, AAD.toByteArray(), cipher, encrypt = false)
            } catch (e: AEADBadTagException) {
                throw DmvWrongPassword()
            }
        } else {
            Base64.decode(inner.cipher)
        }
        if (inner.maxSizeBytes != null && payloadBytes.size > inner.maxSizeBytes) throw DmvTooLarge()
        return String(payloadBytes, Charsets.UTF_8)
    }

    // -- plumbing ------------------------------------------------------------

    private fun header(flags: Byte, innerJson: String): ByteArray {
        val openKey = MessageDigest.getInstance("SHA-256")
            .digest("Damavand .dmv v1".toByteArray(Charsets.US_ASCII))
        val nonce = ByteArray(NONCE_LEN).also(rng::nextBytes)
        val outer = aesGcm(openKey, nonce, AAD.toByteArray(), innerJson.toByteArray(), encrypt = true)
        return MAGIC + byteArrayOf(VERSION) + byteArrayOf(flags) + nonce + outer
    }

    private fun envelopeJson(
        name: String,
        body: ByteArray,
        expiresAt: Long?,
        maxSizeBytes: Long?,
        kdf: DmvKdf?,
    ): String {
        val noncePart = if (kdf != null) body.copyOfRange(0, NONCE_LEN) else ByteArray(0)
        val cipherPart = if (kdf != null) body.copyOfRange(NONCE_LEN, body.size) else body
        return json.encodeToString(
            Envelope.serializer(),
            Envelope(
                name = name,
                payload = "",
                createdAt = currentTimeMillis(),
                expiresAt = expiresAt,
                maxSizeBytes = maxSizeBytes,
                kdf = kdf,
                nonce = Base64.encode(noncePart),
                cipher = Base64.encode(cipherPart),
            ),
        )
    }

    private fun readOuter(bytes: ByteArray): Pair<Byte, Envelope> {
        if (bytes.size < MAGIC.size + 3 + NONCE_LEN + 16) throw DmvTampered()
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw DmvTampered()
        if (bytes[MAGIC.size] != VERSION) throw DmvTampered()
        val flags = bytes[MAGIC.size + 1]
        val openKey = MessageDigest.getInstance("SHA-256")
            .digest("Damavand .dmv v1".toByteArray(Charsets.US_ASCII))
        val nonceStart = MAGIC.size + 2
        val nonce = bytes.copyOfRange(nonceStart, nonceStart + NONCE_LEN)
        val outer = bytes.copyOfRange(nonceStart + NONCE_LEN, bytes.size)
        val innerBytes = try {
            aesGcm(openKey, nonce, AAD.toByteArray(), outer, encrypt = false)
        } catch (e: AEADBadTagException) {
            throw DmvTampered()
        }
        val inner = try {
            json.decodeFromString(Envelope.serializer(), String(innerBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw DmvTampered()
        }
        return flags to inner
    }

    private fun argon2(password: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withMemoryAsKB(ARGON_MEM_KIB)
            .withIterations(ARGON_ITERATIONS)
            .withParallelism(ARGON_PARALLELISM)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(KEY_LEN)
        generator.generateBytes(password.toCharArray(), out)
        return out
    }

    private fun aesGcm(key: ByteArray, nonce: ByteArray, aad: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(aad)
        return if (encrypt) cipher.doFinal(data) else cipher.doFinal(data)
    }
}

fun dmvFileName(name: String): String {
    val base = name.trim().ifBlank { "config" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
    return if (base.endsWith(".dmv")) base else "$base.dmv"
}
