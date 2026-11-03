/*
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the
 * owner's own fork lineage of that codebase.
 *
 * Structural port of ZedSecure's Pixel home (PixelHome.kt, 926 lines +
 * PixelDetails.kt) to v2 state: a pixel-hero layout — big status hero →
 * connect control → active config → details. Zed-specific dependencies
 * (its settings types, vault, map, NetworkInfo) are dropped; where Zed's
 * Pixel showed panels with no v2 backend, this uses v2's DashboardCard
 * and the deferred IpDetails/SpeedTest surfaces instead. The hero's
 * morphing cookie-shape door is simplified to v2's ConnectPill /
 * SlideConnect control, per the v2 task spec.
 */
package com.amyrmo.damavand.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.data.settings.ConnectStyle
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.platform.GeoInfo
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.components.ConnectPill
import com.amyrmo.damavand.ui.components.ConnectPillState
import com.amyrmo.damavand.ui.components.ConnectionStageIndicator
import com.amyrmo.damavand.ui.components.SlideConnect
import com.amyrmo.damavand.ui.theme.Dimens

/**
 * The Pixel home, in Zed's layout order: hero → connect → config →
 * details (dashboard + IP panel + speed test when connected).
 */
@Composable
fun PixelHome(
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

        // 1 — hero.
        PixelHeroCard(state = session, geo = geo)

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
            // 2 — connect control (same Appearance switch as ClassicHome).
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

            // 3 — active config.
            ActiveConfigCard(profile = profile, onClick = onOpenServers)

            // 4 — details.
            if (session is SessionState.Connected) {
                DashboardCard(
                    state = session as SessionState.Connected,
                    geo = geo,
                    traffic = traffic,
                )
                IpDetailsPanel(
                    geo = geo,
                    socksPort = (session as SessionState.Connected).localSocksPort,
                )
                SpeedTestCard()
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

/**
 * Zed Pixel's hero card (44 dp corners, primaryContainer when live):
 * the session status as the headline, a progress line while connecting,
 * and — when connected — the fact chips (location / ping / exit IP)
 * that in Zed opened its PixelDetails panel. Here the full details live
 * in the section below the config card instead.
 */
@Composable
private fun PixelHeroCard(
    state: SessionState,
    geo: GeoInfo?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val live = state is SessionState.Connected
    val container by animateColorAsState(
        targetValue = if (live) scheme.primaryContainer else scheme.surfaceContainer,
        animationSpec = tween(700),
        label = "pixel-hero-container",
    )
    val ink by animateColorAsState(
        targetValue = if (live) scheme.onPrimaryContainer else scheme.onSurface,
        animationSpec = tween(700),
        label = "pixel-hero-ink",
    )
    val sub = if (live) {
        scheme.onPrimaryContainer.copy(alpha = 0.78f)
    } else {
        scheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(44.dp),
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    when (state) {
                        is SessionState.Connected -> R.string.home_status_connected
                        is SessionState.Connecting -> R.string.home_status_connecting
                        is SessionState.Failed -> R.string.home_status_failed
                        SessionState.Idle -> R.string.home_status_idle
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = if (state is SessionState.Failed) scheme.error else ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            when (state) {
                SessionState.Idle -> Text(
                    // TODO string key: v1/Zed's portal_hint has no v2 equivalent yet.
                    text = "Tap connect to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sub,
                    textAlign = TextAlign.Center,
                )

                is SessionState.Connecting -> {
                    LinearProgressIndicator(
                        modifier = Modifier.width(156.dp),
                        color = scheme.primary,
                        trackColor = scheme.primary.copy(alpha = 0.18f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.connecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = sub,
                    )
                }

                is SessionState.Connected -> PixelFacts(geo = geo)

                is SessionState.Failed -> Text(
                    text = stringResource(R.string.connection_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sub,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Zed Pixel's fact chips, fed by v2's real [GeoInfo] instead of NetworkInfo. */
@Composable
private fun PixelFacts(geo: GeoInfo?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val place = listOfNotNull(geo?.city, geo?.country)
            .joinToString(", ")
            .ifBlank { "…" }
        PixelFactChip(text = place)
        geo?.pingMs?.let { PixelFactChip(text = "$it ms") }
        geo?.ip?.let { PixelFactChip(text = it) }
    }
}

@Composable
private fun PixelFactChip(text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.onPrimaryContainer.copy(alpha = 0.09f),
        contentColor = scheme.onPrimaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
