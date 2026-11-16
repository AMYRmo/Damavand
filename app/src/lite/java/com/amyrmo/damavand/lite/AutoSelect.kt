package com.amyrmo.damavand.lite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Auto-select (3.2.8) — a faithful port of Damavand's Auto-select
 * (ZedSecure's "Auto"), which itself is the autoselect outbound of
 * CluvexStudio's Xray-core fork. The model is copied from Damavand's
 * core/AutoSelect.kt + domain/config/AutoSelectConfig.kt:
 *
 * - Auto is a SELECTABLE server, not a toggle: one pseudo-profile per
 *   scope — "auto:all" (every config), "auto:sub:<subscription url>"
 *   (one subscription), "auto:manual" (hand-added configs). Tapping it
 *   selects it; the ring connects through it.
 * - The core measures every member (probe every 30 s while active, a
 *   full sweep every 10 min) and switches the exit only when another
 *   member is clearly faster (30% + 60 ms, after 1 min on the current
 *   one) or the current one fails — transparently, without closing
 *   working connections. A failed connection is retried on the next
 *   member without the app noticing.
 * - The core exposes its live status as JSON (AutoSelectStatus); this
 *   object polls it, tracks the selected member, the switch history
 *   and per-member health, and the UI shows the live line
 *   ("<member> · <delay> ms") exactly like Damavand.
 */
object AutoSelectIds {
    const val ALL = "auto:all"
    const val MANUAL = "auto:manual"
    private const val SUBSCRIPTION = "auto:sub:"

    /** The auto id for a group key ("__all__" = all, "" = manual, else a subscription URL). */
    fun ofGroup(groupKey: String): String = when (groupKey) {
        "__all__" -> ALL
        "" -> MANUAL
        else -> SUBSCRIPTION + groupKey
    }

    fun of(subscriptionUrl: String?): String = when (subscriptionUrl) {
        null -> ALL
        "" -> MANUAL
        else -> SUBSCRIPTION + subscriptionUrl
    }

    fun isAuto(id: String?): Boolean = id != null &&
        (id == ALL || id == MANUAL || (id.startsWith(SUBSCRIPTION) && id.length > SUBSCRIPTION.length))

    /** The group key ("__all__" / "" / subscription URL) behind an auto id. */
    fun groupKeyOf(id: String): String = when {
        id == ALL -> "__all__"
        id == MANUAL -> ""
        id.startsWith(SUBSCRIPTION) -> id.removePrefix(SUBSCRIPTION)
        else -> throw IllegalArgumentException("not an auto-select id: $id")
    }
}

/** Tuning for one auto-select group — Damavand's defaults. */
data class AutoSelectTuning(
    val probeUrl: String = "",
    val probeTimeoutSec: Int = 5,
    val activeIntervalSec: Int = 30,
    val sweepIntervalMin: Int = 10,
    val switchMarginPercent: Int = 30,
    val retry: Boolean = true,
    val initialProfileId: String? = null,
)

object AutoSelect {
    var readStatus: () -> String? = { XrayController.autoSelectStatus() }

    var pinMember: (group: String, member: String) -> Boolean =
        { group, member -> XrayController.autoSelectPin(group, member) }

    var networkChanged: () -> Unit = { XrayController.autoSelectNetworkChanged() }

    var supportsPinning: Boolean = true

    data class Session(
        val profileId: String,
        val memberProfiles: Map<String, String>,
        val status: Status? = null,
    ) {
        val selectedProfileId: String? get() = status?.selected?.let { memberProfiles[it] }

        val exitGeneration: Int get() = ((status?.switches ?: 0) - 1).coerceAtLeast(0)
    }

    fun exitGeneration(): Int = _session.value?.exitGeneration ?: 0

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    @Volatile
    private var pending: Session? = null

    fun prepare(profileId: String, memberProfiles: Map<String, String>) {
        pending = Session(profileId, memberProfiles)
    }

    fun clearPrepared() {
        pending = null
    }

    fun activate(): Boolean {
        val next = pending ?: return false
        pending = null
        _session.value = next
        return true
    }

    fun end() {
        _session.value = null
    }

    /** Poll the core's status JSON; returns the new switch events. */
    fun poll(): List<Event> {
        val current = _session.value ?: return emptyList()

        val status = parse(readStatus()).firstOrNull { it.tag == "proxy" }
            ?.let { s -> s.copy(members = s.members.map { it.copy(lastProbeAgoMs = -1, lastSuccessAgoMs = -1) }) }
            ?: return emptyList()
        val previous = current.status
        if (status != previous) _session.value = current.copy(status = status)
        val seenUntil = previous?.events?.maxOfOrNull { it.atMs } ?: Long.MIN_VALUE
        return if (previous == null) emptyList() else status.events.filter { it.atMs > seenUntil }
    }

    fun pin(memberTag: String): Boolean = pinMember("proxy", memberTag).also { if (it) poll() }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(raw: String?): List<Status> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Status.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    @Serializable
    data class Status(
        val tag: String = "",
        val selected: String = "",
        val pinned: String? = null,
        val state: String = "starting",
        val switches: Int = 0,
        val events: List<Event> = emptyList(),
        val members: List<Member> = emptyList(),
    ) {
        val phase: Phase get() = Phase.of(state)

        fun member(tag: String): Member? = members.firstOrNull { it.tag == tag }
    }

    @Serializable
    data class Event(
        val from: String = "",
        val to: String = "",
        val reason: String = "",
        val atMs: Long = 0,
    ) {
        val kind: Reason get() = Reason.of(reason)
    }

    @Serializable
    data class Member(
        val tag: String = "",
        val state: String = "unknown",
        val delayMs: Long = -1,
        val lastDelayMs: Long = -1,
        val jitterMs: Long = 0,
        val samples: Int = 0,
        val score: Double = -1.0,
        val failRatio: Double = 0.0,
        val connections: Int = 0,
        val lastProbeAgoMs: Long = -1,
        val lastSuccessAgoMs: Long = -1,
        val lastError: String? = null,
    ) {
        val health: Health get() = Health.of(state)
    }

    enum class Phase {
        Starting, Ok, Degraded, Down, Idle;

        companion object {
            fun of(s: String) = when (s) {
                "ok" -> Ok
                "degraded" -> Degraded
                "down" -> Down
                "idle" -> Idle
                else -> Starting
            }
        }
    }

    enum class Health {
        Unknown, Alive, Suspect, Dead;

        companion object {
            fun of(s: String) = when (s) {
                "alive" -> Alive
                "suspect" -> Suspect
                "dead" -> Dead
                else -> Unknown
            }
        }
    }

    enum class Reason {
        Startup, Failover, Faster, Pinned, Recovered;

        companion object {
            fun of(s: String) = when (s) {
                "failover" -> Failover
                "faster" -> Faster
                "pinned" -> Pinned
                "recovered" -> Recovered
                else -> Startup
            }
        }
    }
}
