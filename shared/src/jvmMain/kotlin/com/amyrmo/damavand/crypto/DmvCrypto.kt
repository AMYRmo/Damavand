package com.amyrmo.damavand.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * .dmv container.
 *
 *  v2 (current, compact binary):  MAGIC(4) | 0x02 | outerNonce(12) | AES-GCM(openKey, inner)
 *      inner = mode(1) flags(1) createdAt(8) [expiresAt(8)] name note
 *              [salt(16) memKiB(4) iters(1) par(1)]  [maxSizeBytes(8)]  cfgNonce(12) cfgCipher(rest)
 *      cfgCipher = AES-GCM(configKey, deflate(config))   (deflate only when it makes it smaller)
 *      configKey = openKey when no password, Argon2id(password) otherwise.
 *
 *  v1 (JSON envelope wrapped in AES-GCM with the v1 open key) is still readable,
 *  so .dmv files shared by older Damavand builds keep working.
 */
@OptIn(ExperimentalEncodingApi::class)
actual object DmvCrypto {
    private val MAGIC = "DMV1".toByteArray(Charsets.US_ASCII)
    private const val VERSION_V1: Byte = 1
    private const val VERSION: Byte = 2
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_LEN = 32
    private const val SALT_LEN = 16

    private const val ARGON_MEM_KIB = 65_536
    private const val ARGON_ITERATIONS = 3
    private const val ARGON_PARALLELISM = 1

    private const val FLAG_EXPIRY = 1
    private const val FLAG_DEFLATE = 4
    private const val FLAG_DEVICE = 8
    private const val FLAG_SIZE = 16

    private const val MAX_PLAIN_BYTES = 8 * 1024 * 1024

    private const val CFG_AAD = "dmv-cfg-v1"
    private const val V1_AAD = "dmv-share-v1"

    private val openKey: ByteArray =
        MessageDigest.getInstance("SHA-256").digest("Damavand .dmv v2".toByteArray(Charsets.US_ASCII))
    private val openKeyV1: ByteArray =
        MessageDigest.getInstance("SHA-256").digest("Damavand .dmv v1".toByteArray(Charsets.US_ASCII))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val rng = SecureRandom()

    actual fun secureRandomBytes(n: Int): ByteArray = randomBytes(n)

    actual fun claimProof(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("dmv-claim:${key.trim()}".toByteArray(Charsets.UTF_8))
        return Base64.encode(digest).trimEnd('=').replace('+', '-').replace('/', '_')
    }

    actual fun seal(request: DmvSealRequest): ByteArray {
        val payloadBytes = request.configPayload.toByteArray(Charsets.UTF_8)
        if (payloadBytes.size > MAX_PLAIN_BYTES) throw DmvTooLargeException()
        if (request.maxSizeBytes != null && payloadBytes.size > request.maxSizeBytes) throw DmvTooLargeException()

        val cfgNonce = randomBytes(NONCE_LEN)
        val mode: Int
        var salt: ByteArray? = null
        val configKey: ByteArray
        val password = request.password
        if (password.isNullOrEmpty()) {
            mode = 0
            configKey = openKey
        } else {
            mode = 1
            val s = randomBytes(SALT_LEN)
            salt = s
            configKey = argon2(password, s, ARGON_MEM_KIB, ARGON_ITERATIONS, ARGON_PARALLELISM)
        }

        val packed = deflate(payloadBytes)
        val useDeflate = packed.size < payloadBytes.size
        val cfgCipher = aesGcmEncrypt(
            configKey, cfgNonce, CFG_AAD.toByteArray(), if (useDeflate) packed else payloadBytes,
        )

        var flags = 0
        if (request.expiresAt != null) flags = flags or FLAG_EXPIRY
        if (useDeflate) flags = flags or FLAG_DEFLATE
        if (request.deviceBound) flags = flags or FLAG_DEVICE
        if (request.maxSizeBytes != null) flags = flags or FLAG_SIZE

        val buf = ByteArrayOutputStream(cfgCipher.size + 128)
        val d = DataOutputStream(buf)
        d.writeByte(mode)
        d.writeByte(flags)
        d.writeLong(System.currentTimeMillis())
        request.expiresAt?.let { d.writeLong(it) }
        writeStr(d, request.name)
        writeStr(d, request.note)
        if (mode == 1) {
            d.write(salt!!)
            d.writeInt(ARGON_MEM_KIB)
            d.writeByte(ARGON_ITERATIONS)
            d.writeByte(ARGON_PARALLELISM)
        }
        if (request.maxSizeBytes != null) d.writeLong(request.maxSizeBytes)
        d.write(cfgNonce)
        d.write(cfgCipher)
        d.flush()
        val innerBytes = buf.toByteArray()

        val outerNonce = randomBytes(NONCE_LEN)
        val outerCipher = aesGcmEncrypt(openKey, outerNonce, aad(MAGIC, VERSION), innerBytes)

        return MAGIC + byteArrayOf(VERSION) + outerNonce + outerCipher
    }

    actual fun peek(bytes: ByteArray): DmvMetadata {
        if (isV1(bytes)) {
            val (flags, env) = readV1Outer(bytes)
            return DmvMetadata(
                passwordProtected = flags.toInt() and 1 == 1,
                name = env.name,
                note = "",
                createdAt = env.createdAt,
                expiresAt = env.expiresAt,
                deviceBound = false,
                maxSizeBytes = env.maxSizeBytes,
            )
        }
        val inner = readInner(bytes)
        return DmvMetadata(
            passwordProtected = inner.mode == 1,
            name = inner.name,
            note = inner.note,
            createdAt = inner.createdAt,
            expiresAt = inner.expiresAt,
            deviceBound = inner.deviceBound,
            maxSizeBytes = inner.maxSizeBytes,
        )
    }

    actual fun open(bytes: ByteArray, password: String?): String {
        if (isV1(bytes)) return openV1(bytes, password)
        val inner = readInner(bytes)
        val exp = inner.expiresAt
        if (exp != null && System.currentTimeMillis() > exp) {
            throw DmvExpiredException()
        }

        val configKey: ByteArray = if (inner.mode == 1) {
            if (password.isNullOrEmpty()) throw DmvPasswordRequiredException()
            val salt = inner.salt ?: throw DmvTamperException()
            argon2(password, salt, inner.memKiB, inner.iterations, inner.parallelism)
        } else {
            openKey
        }
        val plain = try {
            aesGcmDecrypt(configKey, inner.cfgNonce, CFG_AAD.toByteArray(), inner.cfgCipher)
        } catch (e: AEADBadTagException) {
            if (inner.mode == 1) throw DmvWrongPasswordException() else throw DmvTamperException()
        }
        val raw = if (inner.deflated) inflate(plain) else plain
        if (inner.maxSizeBytes != null && raw.size > inner.maxSizeBytes) throw DmvTooLargeException()
        return String(raw, Charsets.UTF_8)
    }

    // -- v2 inner ------------------------------------------------------------

    private class Inner(
        val mode: Int,
        val name: String,
        val note: String,
        val createdAt: Long,
        val expiresAt: Long?,
        val salt: ByteArray?,
        val memKiB: Int,
        val iterations: Int,
        val parallelism: Int,
        val cfgNonce: ByteArray,
        val cfgCipher: ByteArray,
        val deflated: Boolean,
        val deviceBound: Boolean,
        val maxSizeBytes: Long?,
    )

    private fun readInner(bytes: ByteArray): Inner {
        if (bytes.size < 4 + 1 + NONCE_LEN + 16) throw DmvTamperException()
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC)) throw DmvTamperException()
        val version = bytes[4]
        if (version != VERSION) throw DmvVersionException()
        val nonceStart = 4 + 1
        val outerNonce = bytes.copyOfRange(nonceStart, nonceStart + NONCE_LEN)
        val outerCipher = bytes.copyOfRange(nonceStart + NONCE_LEN, bytes.size)
        val innerBytes = try {
            aesGcmDecrypt(openKey, outerNonce, aad(MAGIC, version), outerCipher)
        } catch (e: AEADBadTagException) {
            throw DmvTamperException()
        }
        return parseV2(innerBytes)
    }

    private fun parseV2(buf: ByteArray): Inner {
        try {
            val d = DataInputStream(ByteArrayInputStream(buf))
            val mode = d.readUnsignedByte()
            val flags = d.readUnsignedByte()
            if (mode != 0 && mode != 1) throw DmvTamperException()
            val createdAt = d.readLong()
            val expiresAt = if ((flags and FLAG_EXPIRY) != 0) d.readLong() else null
            val name = readStr(d)
            val note = readStr(d)
            var salt: ByteArray? = null
            var mem = ARGON_MEM_KIB
            var iters = ARGON_ITERATIONS
            var par = ARGON_PARALLELISM
            if (mode == 1) {
                salt = ByteArray(SALT_LEN).also { d.readFully(it) }
                mem = d.readInt()
                iters = d.readUnsignedByte()
                par = d.readUnsignedByte()
                // A crafted link must not be able to ask for gigabytes of RAM.
                if (mem !in 8_192..262_144 || iters !in 1..10 || par !in 1..4) throw DmvTamperException()
            }
            val maxSize = if ((flags and FLAG_SIZE) != 0) d.readLong() else null
            val nonce = ByteArray(NONCE_LEN).also { d.readFully(it) }
            val cipher = d.readBytes()
            if (cipher.size < 16) throw DmvTamperException()
            return Inner(
                mode, name, note, createdAt, expiresAt,
                salt, mem, iters, par, nonce, cipher,
                (flags and FLAG_DEFLATE) != 0, (flags and FLAG_DEVICE) != 0, maxSize,
            )
        } catch (e: IOException) {
            throw DmvTamperException()
        }
    }

    // -- v1 compat -------------------------------------------------------------

    private fun isV1(bytes: ByteArray): Boolean =
        bytes.size >= 5 && bytes.copyOfRange(0, 4).contentEquals(MAGIC) && bytes[4] == VERSION_V1

    private fun readV1Outer(bytes: ByteArray): Pair<Byte, V1Envelope> {
        if (bytes.size < MAGIC.size + 3 + NONCE_LEN + 16) throw DmvTamperException()
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw DmvTamperException()
        if (bytes[MAGIC.size] != VERSION_V1) throw DmvTamperException()
        val flags = bytes[MAGIC.size + 1]
        val nonceStart = MAGIC.size + 2
        val nonce = bytes.copyOfRange(nonceStart, nonceStart + NONCE_LEN)
        val outer = bytes.copyOfRange(nonceStart + NONCE_LEN, bytes.size)
        val innerBytes = try {
            aesGcmDecrypt(openKeyV1, nonce, V1_AAD.toByteArray(), outer)
        } catch (e: AEADBadTagException) {
            throw DmvTamperException()
        }
        val inner = try {
            json.decodeFromString(V1Envelope.serializer(), String(innerBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw DmvTamperException()
        }
        return flags to inner
    }

    private fun openV1(bytes: ByteArray, password: String?): String {
        val (flags, inner) = readV1Outer(bytes)
        if (inner.expiresAt != null && System.currentTimeMillis() > inner.expiresAt) throw DmvExpiredException()
        val payloadBytes: ByteArray = if (flags.toInt() and 1 == 1) {
            if (password.isNullOrEmpty()) throw DmvPasswordRequiredException()
            val kdf = inner.kdf ?: throw DmvTamperException()
            val key = argon2(password, Base64.decode(kdf.salt), ARGON_MEM_KIB, ARGON_ITERATIONS, ARGON_PARALLELISM)
            val nonce = Base64.decode(inner.nonce)
            val cipher = Base64.decode(inner.cipher)
            try {
                aesGcmDecrypt(key, nonce, V1_AAD.toByteArray(), cipher)
            } catch (e: AEADBadTagException) {
                throw DmvWrongPasswordException()
            }
        } else {
            Base64.decode(inner.cipher)
        }
        if (inner.maxSizeBytes != null && payloadBytes.size > inner.maxSizeBytes) throw DmvTooLargeException()
        return String(payloadBytes, Charsets.UTF_8)
    }

    // -- plumbing ---------------------------------------------------------------

    private fun aad(magic: ByteArray, version: Byte): ByteArray = magic + byteArrayOf(version)

    private fun writeStr(d: DataOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        val len = minOf(b.size, 65_535)
        d.writeShort(len)
        d.write(b, 0, len)
    }

    private fun readStr(d: DataInputStream): String {
        val len = d.readUnsignedShort()
        val b = ByteArray(len)
        d.readFully(b)
        return String(b, Charsets.UTF_8)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(maxOf(64, data.size / 2))
            val chunk = ByteArray(8192)
            while (!deflater.finished()) {
                val n = deflater.deflate(chunk)
                out.write(chunk, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 3)
            val chunk = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(chunk)
                if (n == 0) throw DmvTamperException()
                out.write(chunk, 0, n)
                if (out.size() > MAX_PLAIN_BYTES) throw DmvTamperException()
            }
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw DmvTamperException()
        } finally {
            inflater.end()
        }
    }

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain)
    }

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(cipherText)
    }

    private fun argon2(password: String, salt: ByteArray, memKiB: Int, iterations: Int, parallelism: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memKiB)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(KEY_LEN)
        generator.generateBytes(password.toCharArray(), out)
        return out
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }
}

@Serializable
private data class V1Envelope(
    val name: String,
    val payload: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val maxSizeBytes: Long? = null,
    val kdf: V1Kdf? = null,
    val nonce: String,
    val cipher: String,
)

@Serializable
private data class V1Kdf(
    val salt: String,
    val memKiB: Int,
    val iterations: Int,
    val parallelism: Int,
)
