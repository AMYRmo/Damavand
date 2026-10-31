package com.amyrmo.damavand.core

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream

/**
 * The server list Psiphon's client starts with, ported from PattNG's
 * PsiphonServerList. It is the signed, compressed list the client downloads
 * for itself, shipped in the app's assets as [PSIPHON_SERVERS_DAT] by
 * tools/fetch-psiphon-servers.sh. The client takes it only as a plain file of
 * server entries, so the list is checked and unpacked beside the identity
 * files once per version of the file. A list without Psiphon's signature is
 * not handed on: the client trusts what it is given.
 *
 * Damavand reads the list out of its (read-only) APK assets, so the packed
 * bytes are handed in rather than read from a user-replaceable asset folder
 * the way PattNG does it; the unpacking and checking are PattNG's.
 */
object PsiphonServerList {

    /** The bundled list's asset name. */
    const val PSIPHON_SERVERS_DAT = "psiphon_servers.dat"

    /** Psiphon's public key for its server list, the one its client checks downloads with; the core carries the same key. */
    internal const val SIGNING_KEY =
        "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH" +
            "5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5" +
            "OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42Kcot" +
            "LFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6" +
            "/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7G" +
            "stZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1O" +
            "geF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8" +
            "u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz" +
            "31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xal" +
            "KxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="

    private const val ENTRIES_FILE = "psiphon-servers.txt"
    private const val SOURCE_MARK = "psiphon-servers.source"

    /**
     * The entries file for the bundled list [packed], unpacked into [workDir]
     * when the list is new or has changed since; the entries kept from before
     * when the list cannot be used, with the reason given to [onProblem], and
     * null when there is no usable list at all.
     */
    fun entriesFile(packed: ByteArray, workDir: File, signingKey: String = SIGNING_KEY, onProblem: (IOException) -> Unit = {}): File? {
        if (packed.isEmpty()) return null
        val entries = File(workDir, ENTRIES_FILE)
        val mark = File(workDir, SOURCE_MARK)
        val crc = CRC32().apply { update(packed) }.value
        val stamp = "${packed.size}:$crc"
        if (entries.isFile && mark.isFile && runCatching { mark.readText() }.getOrNull() == stamp) return entries
        return try {
            val text = unpack(packed, signingKey)
            // The daemon and the editor may both unpack a new list; each writes under a name of its own.
            val fresh = File(workDir, "$ENTRIES_FILE.${System.nanoTime()}.new")
            fresh.writeText(text)
            if (!fresh.renameTo(entries)) {
                entries.delete()
                if (!fresh.renameTo(entries)) throw IOException("could not put ${entries.name} in place")
            }
            mark.writeText(stamp)
            entries
        } catch (e: IOException) {
            onProblem(e)
            entries.takeIf { it.isFile }
        }
    }

    /**
     * The server entries inside [packed], a signed, compressed list as Psiphon writes it; throws
     * when it is not one, or is not signed with [signingKey]. Psiphon names the key by the digest
     * of its text as written, not of the key's bytes, and signs the entry text with it.
     */
    @Throws(IOException::class)
    internal fun unpack(packed: ByteArray, signingKey: String): String {
        val text = try {
            InflaterInputStream(ByteArrayInputStream(packed)).use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (e: IOException) {
            throw IOException("the list is not compressed the way Psiphon writes it", e)
        }
        val json = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: RuntimeException) {
            throw IOException("the list is not the package Psiphon writes", e)
        }
        val data = json.stringField("data") ?: throw IOException("the list carries no entries")
        val keyDigest = json.stringField("signingPublicKeyDigest") ?: throw IOException("the list names no key")
        val signature = json.stringField("signature") ?: throw IOException("the list is not signed")
        try {
            val expected = MessageDigest.getInstance("SHA-256").digest(signingKey.toByteArray(Charsets.US_ASCII))
            if (!Base64.decode(keyDigest, Base64.DEFAULT).contentEquals(expected)) throw IOException("the list is signed with another key")
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(signingKey, Base64.DEFAULT)))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(data.toByteArray(Charsets.UTF_8))
            if (!verifier.verify(Base64.decode(signature, Base64.DEFAULT))) throw IOException("the signature of the list does not check out")
        } catch (e: GeneralSecurityException) {
            throw IOException("the signature of the list cannot be checked", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("the list is not encoded the way Psiphon writes it", e)
        }
        if (data.lineSequence().none { it.isNotBlank() }) throw IOException("the list is empty")
        return data
    }

    /** The string [name] of this package, or null when it is absent or not a string. */
    private fun JsonObject.stringField(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
