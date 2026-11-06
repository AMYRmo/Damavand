package com.amyrmo.damavand.platform

internal expect fun secureRandomToken(length: Int): String

/** Cryptographically secure random bytes (BPB Cloudflare port: UUIDs and deployment names). */
internal expect fun secureRandomBytes(count: Int): ByteArray

internal const val TOKEN_ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
