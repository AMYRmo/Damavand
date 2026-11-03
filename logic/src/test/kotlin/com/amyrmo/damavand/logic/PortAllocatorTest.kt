package com.amyrmo.damavand.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PortAllocator (§6.2): deterministic, collision-free assignment.
 * All tests inject the probe so they never touch real sockets.
 */
class PortAllocatorTest {

    @Test
    fun `allocation is deterministic from the range start`() {
        val a = PortAllocator(range = 20000..20009) { true }
        assertEquals(listOf(20000, 20001, 20002), listOf(a.allocate(), a.allocate(), a.allocate()))

        val b = PortAllocator(range = 20000..20009) { true }
        assertEquals(listOf(20000, 20001, 20002), b.allocateAll(3))
    }

    @Test
    fun `occupied ports are skipped by the probe`() {
        val occupied = setOf(20000, 20002)
        val a = PortAllocator(range = 20000..20009) { it !in occupied }
        assertEquals(20001, a.allocate())
        assertEquals(20003, a.allocate())
    }

    @Test
    fun `no port is handed out twice`() {
        val a = PortAllocator(range = 20000..20099) { true }
        val ports = a.allocateAll(50)
        assertEquals(50, ports.toSet().size)
        assertEquals(ports.toSet(), a.allocatedPorts)
    }

    @Test
    fun `released ports can be handed out again`() {
        val a = PortAllocator(range = 20000..20001) { true }
        val first = a.allocate()
        a.allocate()
        assertThrows(NoSuchElementException::class.java) { a.allocate() }
        a.release(first)
        assertEquals(first, a.allocate())
    }

    @Test
    fun `exhaustion throws instead of colliding`() {
        val a = PortAllocator(range = 20000..20000) { false }
        assertThrows(NoSuchElementException::class.java) { a.allocate() }
    }

    @Test
    fun `sessions are isolated but probing keeps them apart`() {
        // Two allocators over the same range with the same probe vision:
        // each is internally collision-free; cross-session safety comes
        // from the real bind probe, modelled here by a shared "taken" set.
        val taken = mutableSetOf<Int>()
        val probe: (Int) -> Boolean = { it !in taken }
        val s1 = PortAllocator(range = 20000..20009, isFree = probe)
        val p1 = s1.allocate(); taken += p1
        val s2 = PortAllocator(range = 20000..20009, isFree = probe)
        val p2 = s2.allocate()
        assertTrue(p1 != p2)
        assertFalse(s2.isAllocated(p1))
    }

    @Test
    fun `default range matches the spec`() {
        assertEquals(18190..22189, PortAllocator.DEFAULT_RANGE)
    }
}
