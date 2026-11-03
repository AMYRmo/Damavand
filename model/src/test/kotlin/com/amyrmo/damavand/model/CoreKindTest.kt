package com.amyrmo.damavand.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Skeleton smoke test: the six real cores plus the two plan-level kinds. */
class CoreKindTest {
    @Test
    fun `all v2 core kinds are present`() {
        assertEquals(
            listOf("XRAY", "SINGBOX", "PSIPHON", "AETHER", "MIHOMO", "WHITEDNS", "CHAIN", "AUTO"),
            CoreKind.entries.map { it.name },
        )
    }
}
