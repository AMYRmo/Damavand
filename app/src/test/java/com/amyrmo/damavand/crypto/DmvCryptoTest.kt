package com.amyrmo.damavand.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DmvCryptoTest {
    private val sampleConfig =
        "vless://11111111-2222-3333-4444-555555555555@example.com:443?encryption=none&security=reality#Node"

    private fun request(
        password: String?,
        expiresAt: Long? = null,
        deviceBound: Boolean = false,
        maxSizeBytes: Long? = null,
    ) = DmvSealRequest(
        configPayload = sampleConfig,
        name = "Berlin Fast",
        note = "Join @mychannel for updates. 50GB cap.",
        expiresAt = expiresAt,
        password = password,
        deviceBound = deviceBound,
        maxSizeBytes = maxSizeBytes,
    )

    @Test
    fun roundtripWithoutPassword() {
        val file = DmvCrypto.seal(request(password = null))
        assertEquals(sampleConfig, DmvCrypto.open(file, password = null))
    }

    @Test
    fun roundtripWithPassword() {
        val file = DmvCrypto.seal(request(password = "correct horse battery"))
        assertEquals(sampleConfig, DmvCrypto.open(file, password = "correct horse battery"))
    }

    @Test
    fun peekExposesMetadataButNotConfig() {
        val file = DmvCrypto.seal(request(password = "secret"))
        val meta = DmvCrypto.peek(file)
        assertTrue(meta.passwordProtected)
        assertEquals("Berlin Fast", meta.name)
        assertTrue(meta.note.contains("mychannel"))
        assertFalse(meta.deviceBound)
        assertFalse(meta.isExpired)
    }

    @Test
    fun newFilesUseVersionTwo() {
        val file = DmvCrypto.seal(request(password = null))
        val magic = String(file.copyOfRange(0, 4), Charsets.US_ASCII)
        assertEquals("DMV1", magic)
        assertEquals(2, file[4].toInt())
    }

    @Test
    fun configIsNotStoredAsPlainText() {
        val file = String(DmvCrypto.seal(request(password = null)), Charsets.ISO_8859_1)
        assertFalse(file.contains("example.com"))
        assertFalse(file.contains("vless://"))
    }

    @Test(expected = DmvWrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        val file = DmvCrypto.seal(request(password = "the-real-password"))
        DmvCrypto.open(file, password = "not-the-password")
    }

    @Test(expected = DmvPasswordRequiredException::class)
    fun passwordProtectedRequiresPassword() {
        val file = DmvCrypto.seal(request(password = "pw"))
        DmvCrypto.open(file, password = null)
    }

    @Test(expected = DmvTamperException::class)
    fun tamperedFileIsRejected() {
        val file = DmvCrypto.seal(request(password = null))
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        DmvCrypto.open(file, password = null)
    }

    @Test(expected = DmvTamperException::class)
    fun truncatedFileIsRejected() {
        val file = DmvCrypto.seal(request(password = null))
        DmvCrypto.open(file.copyOfRange(0, file.size - 8), password = null)
    }

    @Test(expected = DmvTamperException::class)
    fun otherFilesAreRejected() {
        DmvCrypto.peek("{\"outbounds\":[]}".toByteArray())
    }

    @Test(expected = DmvVersionException::class)
    fun unknownVersionIsRejected() {
        val file = DmvCrypto.seal(request(password = null))
        file[4] = 0x7F
        DmvCrypto.open(file, password = null)
    }

    @Test(expected = DmvExpiredException::class)
    fun expiredFileIsRejected() {
        val file = DmvCrypto.seal(request(password = null, expiresAt = System.currentTimeMillis() - 1000))
        DmvCrypto.open(file, password = null)
    }

    @Test
    fun differentSealsProduceDifferentCiphertext() {
        val a = DmvCrypto.seal(request(password = null))
        val b = DmvCrypto.seal(request(password = null))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun deviceBoundRoundtrip() {
        val file = DmvCrypto.seal(request(password = "DEVICECODE123", deviceBound = true))
        val meta = DmvCrypto.peek(file)
        assertTrue(meta.deviceBound)
        assertTrue(meta.passwordProtected)
        assertEquals(sampleConfig, DmvCrypto.open(file, password = "DEVICECODE123"))
    }

    @Test(expected = DmvTooLargeException::class)
    fun sizeCapIsEnforcedAtSeal() {
        DmvCrypto.seal(request(password = null, maxSizeBytes = 8))
    }

    @Test
    fun claimProofIsStableAndKeyed() {
        val a = DmvCrypto.claimProof("one-time-key")
        assertEquals(a, DmvCrypto.claimProof("one-time-key"))
        assertFalse(a == DmvCrypto.claimProof("other-key"))
        assertFalse(a.contains("one-time-key"))
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun largeJsonConfigIsCompressed() {
        val big = buildString {
            append("{\"outbounds\":[")
            repeat(150) { i ->
                if (i > 0) append(',')
                append("{\"tag\":\"node-$i\",\"protocol\":\"vless\",\"settings\":{\"vnext\":[{\"address\":\"n$i.example.com\",\"port\":443}]}}")
            }
            append("]}")
        }
        val file = DmvCrypto.seal(request(password = "k").copy(configPayload = big))
        assertTrue("file ${file.size} should be well under raw ${big.length}", file.size < big.length / 2)
        assertEquals(big, DmvCrypto.open(file, password = "k"))
    }
}
