package com.amyrmo.damavand.core.session

import com.amyrmo.damavand.core.HopFailure
import com.amyrmo.damavand.ui.components.StageInfo

/**
 * The session state the UI observes (architecture §5.5). `Connected`
 * exists only after the end-to-end probe has passed — the state machine
 * has no path around it.
 */
sealed interface SessionState {
    data object Idle : SessionState

    /** A start (or a chain restart) is in flight; [stage] drives the indicator + notification. */
    data class Connecting(
        val sessionId: String,
        val targetName: String,
        val stage: StageInfo,
    ) : SessionState

    data class Connected(
        val sessionId: String,
        val targetName: String,
        /** The session's advertised local proxy port (§2.4 rule 2: from the started artefact). */
        val localSocksPort: Int,
        val startedAtElapsedMs: Long,
    ) : SessionState

    /** A classified failure: which hop, which stage, why (§4.3 step 5). */
    data class Failed(
        val sessionId: String?,
        val targetName: String,
        val failure: HopFailure,
    ) : SessionState
}
