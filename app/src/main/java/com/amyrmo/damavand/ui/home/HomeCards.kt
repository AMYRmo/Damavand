package com.amyrmo.damavand.ui.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.data.profiles.ProfileFactory
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.Subscription
import com.amyrmo.damavand.platform.GeoInfo
import com.amyrmo.damavand.ui.TrafficInfo
import com.amyrmo.damavand.ui.components.ConnectionStage
import com.amyrmo.damavand.ui.components.GlassCard
import com.amyrmo.damavand.ui.formatBytes
import com.amyrmo.damavand.ui.formatDate
import com.amyrmo.damavand.ui.formatDuration
import com.amyrmo.damavand.ui.formatRate
import com.amyrmo.damavand.ui.theme.StateColors
import com.amyrmo.damavand.ui.theme.TrafficColors
import com.amyrmo.damavand.ui.theme.accent
import kotlinx.coroutines.delay

/** Localized stage name (the indicator's own labels live in its file; the banner needs one too). */
@Composable
fun stageName(stage: ConnectionStage): String = stringResource(
    when (stage) {
        ConnectionStage.Starting -> R.string.stage_starting
        ConnectionStage.Authenticating -> R.string.stage_authenticating
        ConnectionStage.Verifying -> R.string.stage_verifying
        ConnectionStage.Retrying -> R.string.stage_retrying
        ConnectionStage.Connected -> R.string.stage_connected
        ConnectionStage.Failed -> R.string.stage_failed
    },
)

/** Brand line + status chip (§5.2 Home, first row). */
@Composable
fun BrandHeader(state: SessionState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (dotColor, statusText) = when (state) {
        is SessionState.Connected -> StateColors.Connected to stringResource(R.string.home_status_connected)
        is SessionState.Connecting -> StateColors.Connecting to stringResource(R.string.home_status_connecting)
        is SessionState.Failed -> StateColors.Error to stringResource(R.string.home_status_failed)
        SessionState.Idle -> scheme.outline to stringResource(R.string.home_status_idle)
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = dotColor,
        )
    }
}

/**
 * The failure banner (§5.2): classified cause up front; tapping expands
 * to the failing hop / kind / stage from the [com.amyrmo.damavand.core.HopFailure].
 */
@Composable
fun FaultBanner(state: SessionState.Failed, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    GlassCard(
        modifier = modifier,
        onClick = { expanded = !expanded },
    ) {
        Text(
            text = state.failure.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = StateColors.Error,
        )
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_fault_details,
                        state.failure.hopIndex + 1,
                        state.failure.kind.name.lowercase(),
                        stageName(state.failure.stage),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = state.failure.profileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The selected config card; tap goes to Servers (§5.2). */
@Composable
fun ActiveConfigCard(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val endpoint = ProfileFactory.endpointOf(profile)
    val subtitle = buildString {
        append(ProfileFactory.protocolLabel(profile))
        if (endpoint != null) append(" · ${endpoint.first}:${endpoint.second}")
    }
    GlassCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(profile.kind.accent()),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The connected dashboard (§5.2): exit IP/location/ping from the lookup
 * through the session's port, live rates + totals, session duration —
 * one merged card (Narcic pattern).
 */
@Composable
fun DashboardCard(
    state: SessionState.Connected,
    geo: GeoInfo?,
    traffic: TrafficInfo,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var now by remember(state.sessionId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.sessionId) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DashboardStat(
                label = stringResource(R.string.home_exit_ip),
                value = geo?.ip ?: "…",
                mono = true,
            )
            DashboardStat(
                label = stringResource(R.string.home_location),
                value = listOfNotNull(geo?.city, geo?.country).joinToString(", ").ifBlank { "…" },
            )
            DashboardStat(
                label = stringResource(R.string.common_ping),
                value = geo?.pingMs?.let { "$it ms" } ?: "…",
                mono = true,
            )
            DashboardStat(
                label = stringResource(R.string.home_session_time),
                value = formatDuration(now - state.startedAtElapsedMs),
                mono = true,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DashboardStat(
                label = "↓ ${stringResource(R.string.home_down)}",
                value = formatRate(traffic.downRateBps),
                valueColor = TrafficColors.Down,
                mono = true,
            )
            DashboardStat(
                label = stringResource(R.string.home_down),
                value = formatBytes(traffic.downTotal),
                mono = true,
            )
            DashboardStat(
                label = "↑ ${stringResource(R.string.home_up)}",
                value = formatRate(traffic.upRateBps),
                valueColor = TrafficColors.Up,
                mono = true,
            )
            DashboardStat(
                label = stringResource(R.string.home_up),
                value = formatBytes(traffic.upTotal),
                mono = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_traffic_note),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardStat(
    label: String,
    value: String,
    mono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
            color = if (valueColor == androidx.compose.ui.graphics.Color.Unspecified) {
                scheme.onSurface
            } else {
                valueColor
            },
            maxLines = 1,
        )
    }
}

/**
 * Subscription health for the active profile (§5.2): quota bar + expiry,
 * explicitly labelled a snapshot (WhiteDNS honesty rule).
 */
@Composable
fun SubscriptionHealthCard(
    subscription: Subscription,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val used = subscription.used
    val total = subscription.total
    GlassCard(modifier = modifier, onClick = onClick) {
        Text(
            text = subscription.title ?: subscription.name,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        if (used != null && total != null && total > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = scheme.primary,
                trackColor = scheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.home_used_of,
                    formatBytes(used),
                    formatBytes(total),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        subscription.expireAt?.let { expireAt ->
            Spacer(Modifier.height(4.dp))
            val daysLeft = ((expireAt - System.currentTimeMillis()) / 86_400_000L).toInt()
            Text(
                text = if (daysLeft >= 0) {
                    stringResource(R.string.home_days_left, daysLeft)
                } else {
                    stringResource(R.string.home_expires, formatDate(expireAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_snapshot_note),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
