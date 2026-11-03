package com.amyrmo.damavand.core.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.amyrmo.damavand.core.session.SessionOrchestrator
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.model.Profile
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The UI's handle on the VPN service (the v2 successor of v1's
 * AndroidVpn/VpnManager pair). `connect` stages the target profile
 * in-process and starts the service with only a token in the intent —
 * the profile itself never crosses an intent boundary (§2.4 rule 4).
 */
object SessionController {

    @Volatile
    private var pending: DamavandVpnService.PendingStart? = null

    @Volatile
    private var orchestrator: SessionOrchestrator? = null

    private val idleState = MutableStateFlow<SessionState>(SessionState.Idle)

    internal fun attach(orchestrator: SessionOrchestrator?) {
        this.orchestrator = orchestrator
    }

    internal fun takePending(token: String?): DamavandVpnService.PendingStart? {
        val staged = pending ?: return null
        if (token != null && staged.token != token) return null
        pending = null
        return staged
    }

    /** The live session state; Idle until the service has started once. */
    fun state(): StateFlow<SessionState> = orchestrator?.state ?: idleState

    fun isActive(): Boolean = orchestrator?.isActive == true

    fun connect(context: Context, target: Profile) {
        val token = UUID.randomUUID().toString()
        pending = DamavandVpnService.PendingStart(token, target)
        val intent = Intent(context, DamavandVpnService::class.java)
            .setAction(DamavandVpnService.ACTION_START)
            .putExtra(DamavandVpnService.EXTRA_TOKEN, token)
        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, DamavandVpnService::class.java)
            .setAction(DamavandVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
