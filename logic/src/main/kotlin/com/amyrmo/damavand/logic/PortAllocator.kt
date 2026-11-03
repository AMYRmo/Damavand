package com.amyrmo.damavand.logic

import java.net.ServerSocket

/**
 * Session-scoped local port allocator (architecture §2.4 rule 2).
 *
 * Ports for chain hops and the bridge come from here — never from a
 * `Random` pick (the v1 scheme both collided between hops and masked
 * leaked processes still holding the old port). Allocation is
 * **deterministic**: candidates are scanned in ascending order from the
 * start of [range], each candidate is bind-probed before it is handed
 * out, and a port is handed out at most once per allocator instance.
 * Two allocators (two sessions) never share state; the bind probe is
 * what keeps them from colliding on the machine.
 *
 * [isFree] is injectable so tests are deterministic; production uses
 * [bindProbe], which actually tries to bind the port on the loopback
 * wildcard the cores will use.
 */
class PortAllocator(
    val range: IntRange = DEFAULT_RANGE,
    private val isFree: (Int) -> Boolean = ::bindProbe,
) {
    private val allocated = LinkedHashSet<Int>()

    /** Every port handed out so far by this allocator, in allocation order. */
    val allocatedPorts: Set<Int> get() = allocated.toSet()

    /**
     * The next free port in the range. Deterministic for a given probe:
     * the same sequence of calls with the same [isFree] answers yields
     * the same ports.
     *
     * @throws NoSuchElementException when the range is exhausted.
     */
    @Synchronized
    fun allocate(): Int {
        for (candidate in range) {
            if (candidate in allocated) continue
            if (!isFree(candidate)) continue
            allocated += candidate
            return candidate
        }
        throw NoSuchElementException("PortAllocator range $range exhausted (${allocated.size} ports in use)")
    }

    /** [count] distinct ports, allocated in order. */
    fun allocateAll(count: Int): List<Int> = List(count) { allocate() }

    /** Give a port back (e.g. a hop that failed to start). Unknown ports are ignored. */
    @Synchronized
    fun release(port: Int) {
        allocated -= port
    }

    fun isAllocated(port: Int): Boolean = port in allocated

    companion object {
        /** The v1 Aether SOCKS base range, widened per §2.4 rule 2. */
        val DEFAULT_RANGE: IntRange = 18190..22189

        /** True when [port] can be bound right now (nothing is listening on it). */
        fun bindProbe(port: Int): Boolean =
            runCatching { ServerSocket(port).use { } }.isSuccess
    }
}
