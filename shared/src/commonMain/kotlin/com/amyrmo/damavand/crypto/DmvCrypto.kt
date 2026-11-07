package com.amyrmo.damavand.crypto

expect object DmvCrypto {
    fun seal(request: DmvSealRequest): ByteArray
    fun peek(bytes: ByteArray): DmvMetadata
    fun open(bytes: ByteArray, password: String?): String

    /** Cryptographically secure random bytes (used for device codes and relay ids). */
    fun secureRandomBytes(n: Int): ByteArray

    /** base64url(SHA-256("dmv-claim:" + key)) — what the relay stores/compares instead of the key itself. */
    fun claimProof(key: String): String
}
