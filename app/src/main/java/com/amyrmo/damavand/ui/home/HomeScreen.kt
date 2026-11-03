package com.amyrmo.damavand.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.data.settings.ConnectStyle
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.components.ConnectPill
import com.amyrmo.damavand.ui.components.ConnectPillState
import com.amyrmo.damavand.ui.components.ConnectionStageIndicator
import com.amyrmo.damavand.ui.components.SlideConnect
import com.amyrmo.damavand.ui.theme.Dimens

/**
 * Home (§5.2): BrandHeader → FaultBanner → ActiveConfigCard → connect
 * control (pill or slide per Appearance) → stage indicator while
 * connecting → DashboardCard when connected → subscription health.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onConnect: (Profile) -> Unit,
    onOpenServers: () -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val session by vm.sessionState.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProfileId.collectAsStateWithLifecycle()
    val geo by vm.geo.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val ui by vm.uiSettings.collectAsStateWithLifecycle()
    val storeFault by vm.storeFault.collectAsStateWithLifecycle()

    val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val pillState = when (session) {
        is SessionState.Connected -> ConnectPillState.Connected
        is SessionState.Connecting -> ConnectPillState.Connecting
        is SessionState.Failed -> ConnectPillState.Failed
        SessionState.Idle -> ConnectPillState.Idle
    }
    val onToggle: () -> Unit = {
        when (session) {
            is SessionState.Connected, is SessionState.Connecting -> vm.disconnect()
            else -> profile?.let(onConnect)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                PaddingValues(
                    start = Dimens.HomePadding,
                    end = Dimens.HomePadding,
                    top = 24.dp,
                    bottom = Dimens.DockClearance + 16.dp,
                ),
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandHeader(state = session)

        if (session is SessionState.Failed) {
            FaultBanner(state = session as SessionState.Failed)
        }
        if (storeFault != null) {
            Text(
                text = storeFault!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (profile == null) {
            Text(
                text = stringResource(R.string.home_no_profile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenServers) {
                Text(stringResource(R.string.servers_add))
            }
        } else {
            ActiveConfigCard(profile = profile, onClick = onOpenServers)

            when (ui.connectStyle) {
                ConnectStyle.PILL -> ConnectPill(
                    state = pillState,
                    sessionId = (session as? SessionState.Connected)?.sessionId?.hashCode() ?: 0,
                    onClick = onToggle,
                )

                ConnectStyle.SLIDE -> SlideConnect(
                    state = pillState,
                    sessionId = (session as? SessionState.Connected)?.sessionId?.hashCode() ?: 0,
                    onToggle = onToggle,
                )
            }

            if (session is SessionState.Connecting) {
                ConnectionStageIndicator(info = (session as SessionState.Connecting).stage)
            }

            if (session is SessionState.Connected) {
                DashboardCard(
                    state = session as SessionState.Connected,
                    geo = geo,
                    traffic = traffic,
                )
            }

            val subscription = profile.subscriptionId?.let { subId ->
                subscriptions.firstOrNull { it.id == subId }
            }
            if (subscription != null) {
                SubscriptionHealthCard(
                    subscription = subscription,
                    onClick = onOpenSubscriptions,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
