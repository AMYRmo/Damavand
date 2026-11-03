/*
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the owner's
 * own fork lineage of that codebase.
 *
 * BentoHome — the Bento home screen, following ZedSecure v3.1.8's
 * BentoHome.kt structure (top bar → fault surface → stage hero server
 * card → speed tiles → session fact tiles → pinned connect control),
 * wired to Damavand v2's state layer ([AppViewModel]: profiles,
 * sessionState, selectedProfileId, geo, traffic, uiSettings) instead of
 * Zed's ConnectionUiState. All strings come from v2's existing R keys;
 * all layout is start/end-based (no physical left/right offsets), and
 * the one offset-driven control (the connect bar's knob) mirrors itself
 * in RTL — see BentoConnectBar.kt.
 */
package com.amyrmo.damavand.ui.bento

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.data.profiles.ProfileFactory
import com.amyrmo.damavand.data.settings.ConnectStyle
import com.amyrmo.damavand.data.settings.ThemeMode
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.formatBytes
import com.amyrmo.damavand.ui.formatDuration
import com.amyrmo.damavand.ui.home.stageName
import kotlinx.coroutines.delay

private const val SPARK_POINTS = 24

@Composable
fun BentoHome(
    vm: com.amyrmo.damavand.ui.AppViewModel,
    onConnect: (com.amyrmo.damavand.model.Profile) -> Unit,
    onOpenServers: () -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    val ui by vm.uiSettings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (ui.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = remember(dark, ui.amoled) {
        BentoPalettes.of("Brand", dark, amoled = ui.amoled)
    }
    val latin = LocalLayoutDirection.current == LayoutDirection.Ltr
    CompositionLocalProvider(LocalBentoStyle provides BentoStyle(colors, latin)) {
        MaterialTheme(colorScheme = bentoColorScheme(colors)) {
            BentoHomeContent(vm, onConnect, onOpenServers, onOpenSubscriptions)
        }
    }
}

@Composable
private fun BentoHomeContent(
    vm: AppViewModel,
    onConnect: (Profile) -> Unit,
    onOpenServers: () -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    val c = Bento.colors
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val session by vm.sessionState.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProfileId.collectAsStateWithLifecycle()
    val geo by vm.geo.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val ui by vm.uiSettings.collectAsStateWithLifecycle()
    val storeFault by vm.storeFault.collectAsStateWithLifecycle()

    val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val live = session is SessionState.Connected
    val connectState = when (session) {
        is SessionState.Connected -> BentoConnectState.Connected
        is SessionState.Connecting -> BentoConnectState.Connecting
        is SessionState.Failed -> BentoConnectState.Failed
        SessionState.Idle -> BentoConnectState.Idle
    }
    val onToggle: () -> Unit = {
        when (session) {
            is SessionState.Connected, is SessionState.Connecting -> vm.disconnect()
            else -> profile?.let(onConnect)
        }
    }

    // Sparkline history, sampled from the live rates (Zed's pattern).
    val sessionId = (session as? SessionState.Connected)?.sessionId
    var downHistory by remember(sessionId) { mutableStateOf(emptyList<Float>()) }
    var upHistory by remember(sessionId) { mutableStateOf(emptyList<Float>()) }
    LaunchedEffect(traffic, live) {
        if (!live) return@LaunchedEffect
        downHistory = (downHistory + traffic.downRateBps.toFloat()).takeLast(SPARK_POINTS)
        upHistory = (upHistory + traffic.upRateBps.toFloat()).takeLast(SPARK_POINTS)
    }

    // Ticking clock for the session-time fact tile.
    var now by remember(sessionId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sessionId) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap + 2.dp),
            ) {
                BentoTopBar(title = stringResource(R.string.app_name))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = BentoMetrics.gutter)
                        .align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(BentoMetrics.gap),
                ) {
                    if (storeFault != null) {
                        Text(
                            text = storeFault!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.bad,
                        )
                    }

                    if (session is SessionState.Failed) {
                        BentoFaultCard(
                            state = session as SessionState.Failed,
                            onRetry = onToggle,
                            onOtherServer = onOpenServers,
                        )
                    }

                    BentoServerHero(
                        profile = profile,
                        session = session,
                        exitIp = if (live) geo?.ip else null,
                        pingMs = if (live) geo?.pingMs else null,
                        onClick = onOpenServers,
                    )

                    if (profile == null) {
                        BentoCard(
                            tone = c.limeTone,
                            modifier = Modifier.fillMaxWidth(),
                            shape = BentoShapes.block(),
                            onClick = onOpenServers,
                        ) {
                            Text(
                                stringResource(R.string.servers_add),
                                style = BentoType.rowTitle(),
                                color = c.onLime,
                                maxLines = 1,
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap),
                    ) {
                        BentoSpeedTile(
                            label = stringResource(R.string.home_down),
                            bps = if (live) traffic.downRateBps else 0,
                            history = downHistory,
                            tone = c.cyan,
                            live = live,
                            modifier = Modifier.weight(1f),
                        )
                        BentoSpeedTile(
                            label = stringResource(R.string.home_up),
                            bps = if (live) traffic.upRateBps else 0,
                            history = upHistory,
                            tone = c.violet,
                            live = live,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (session is SessionState.Connected) {
                        val connected = session as SessionState.Connected
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap),
                        ) {
                            BentoFactTile(
                                label = stringResource(R.string.home_session_time),
                                value = formatDuration(now - connected.startedAtElapsedMs),
                                tone = c.coral.takeIf { c.colorful },
                                modifier = Modifier.weight(1f),
                            )
                            BentoFactTile(
                                label = stringResource(R.string.home_dashboard_title),
                                value = formatBytes(traffic.downTotal + traffic.upTotal),
                                tone = c.cyan.takeIf { c.colorful },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(BentoMetrics.gap),
                        ) {
                            BentoFactTile(
                                label = stringResource(R.string.home_exit_ip),
                                value = geo?.ip ?: "…",
                                modifier = Modifier.weight(1f),
                            )
                            BentoFactTile(
                                label = stringResource(R.string.home_location),
                                value = listOfNotNull(geo?.city, geo?.country)
                                    .joinToString(", ")
                                    .ifBlank { "…" },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = stringResource(R.string.home_traffic_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted,
                        )
                    }

                    val subscription = profile?.subscriptionId?.let { subId ->
                        subscriptions.firstOrNull { it.id == subId }
                    }
                    if (subscription != null) {
                        BentoCard(
                            tone = c.surfaceTone,
                            modifier = Modifier.fillMaxWidth(),
                            shape = BentoShapes.block(),
                            onClick = onOpenSubscriptions,
                        ) {
                            Text(
                                subscription.title ?: subscription.name,
                                style = BentoType.rowTitle(),
                                color = c.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val used = subscription.used
                            val total = subscription.total
                            if (used != null && total != null && total > 0) {
                                Text(
                                    stringResource(R.string.home_used_of, formatBytes(used), formatBytes(total)),
                                    style = BentoType.body(),
                                    color = c.muted,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                stringResource(R.string.home_snapshot_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.muted,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            val stateText = stringResource(
                when (connectState) {
                    BentoConnectState.Connected -> R.string.home_status_connected
                    BentoConnectState.Connecting -> R.string.home_status_connecting
                    BentoConnectState.Failed -> R.string.home_status_failed
                    BentoConnectState.Idle -> R.string.home_status_idle
                },
            )
            val actionText = stringResource(
                when (connectState) {
                    BentoConnectState.Connected -> R.string.disconnect
                    BentoConnectState.Connecting -> R.string.common_cancel
                    else -> R.string.connect
                },
            )
            val hintText = stringResource(
                when (connectState) {
                    BentoConnectState.Connected -> R.string.home_slide_disconnect
                    BentoConnectState.Connecting -> R.string.connecting
                    else -> R.string.home_slide_connect
                },
            )
            BentoConnectControl(
                state = connectState,
                stateText = stateText,
                actionText = actionText,
                hintText = hintText,
                onToggle = onToggle,
                variant = when (ui.connectStyle) {
                    ConnectStyle.PILL -> BentoConnectVariant.Button
                    ConnectStyle.SLIDE -> BentoConnectVariant.Slide
                },
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(start = BentoMetrics.gutter, end = BentoMetrics.gutter, top = 6.dp, bottom = 14.dp),
            )
        }
    }
}

/** Stage hero: the selected profile on the palette's stage tone. */
@Composable
private fun BentoServerHero(
    profile: Profile?,
    session: SessionState,
    exitIp: String?,
    pingMs: Int?,
    onClick: () -> Unit,
) {
    val c = Bento.colors
    val tone = c.stageTone
    BentoCard(
        tone = tone,
        modifier = Modifier.fillMaxWidth(),
        shape = BentoShapes.card(),
        onClick = onClick,
    ) {
        BentoCaption(
            text = stringResource(R.string.dest_servers),
            color = tone.content.copy(alpha = 0.8f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BentoFlag(
                countryCode = null,
                fallback = profile?.name?.take(2) ?: "··",
                size = 50.dp,
                corner = 17.dp,
                container = c.chipOnStage,
                content = tone.content,
            )
            Spacer(Modifier.padding(horizontal = 7.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    profile?.name ?: stringResource(R.string.home_no_profile),
                    style = BentoType.title(27.sp),
                    color = tone.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile != null) {
                    val endpoint = ProfileFactory.endpointOf(profile)
                    Text(
                        buildString {
                            append(ProfileFactory.protocolLabel(profile))
                            if (endpoint != null) append(" · ${endpoint.first}:${endpoint.second}")
                        },
                        style = BentoType.body(),
                        color = tone.content.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "›",
                style = BentoType.title(24.sp),
                color = tone.content.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
        if (profile != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BentoPill(
                    text = stringResource(
                        when (session) {
                            is SessionState.Connected -> R.string.home_status_connected
                            is SessionState.Connecting -> R.string.home_status_connecting
                            is SessionState.Failed -> R.string.home_status_failed
                            SessionState.Idle -> R.string.home_status_idle
                        },
                    ),
                    container = c.chipOnStage,
                    content = tone.content,
                    dot = when (session) {
                        is SessionState.Connected -> c.goodOnStage
                        is SessionState.Connecting -> c.mid
                        is SessionState.Failed -> c.bad
                        SessionState.Idle -> tone.content.copy(alpha = 0.4f)
                    },
                )
                BentoPill(ProfileFactory.protocolLabel(profile), c.lime, c.onLime)
                if (pingMs != null) {
                    BentoPill("$pingMs ms", c.chipOnStage, tone.content, numeric = true)
                }
                if (exitIp != null) {
                    BentoPill(exitIp, c.chipOnStage, tone.content, numeric = true)
                }
            }
        }
    }
}

/** Fault surface for [SessionState.Failed] (Zed's BentoErrorCard, simplified). */
@Composable
private fun BentoFaultCard(
    state: SessionState.Failed,
    onRetry: () -> Unit,
    onOtherServer: () -> Unit,
) {
    val c = Bento.colors
    BentoCard(
        tone = c.coral,
        modifier = Modifier.fillMaxWidth(),
        shape = BentoShapes.hero(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.home_status_failed),
            style = BentoType.title(24.sp),
            color = c.coral.content,
        )
        Text(
            state.failure.reason,
            style = BentoType.body(),
            color = c.coral.content.copy(alpha = 0.85f),
        )
        Text(
            stringResource(
                R.string.home_fault_details,
                state.failure.hopIndex + 1,
                state.failure.kind.name.lowercase(),
                stageName(state.failure.stage),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = c.coral.content.copy(alpha = 0.7f),
        )
        Text(
            state.failure.profileName,
            style = MaterialTheme.typography.bodySmall,
            color = c.coral.content.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BentoPillButton(stringResource(R.string.connect), c.lime, c.onLime, onRetry)
            BentoPillButton(
                stringResource(R.string.dest_servers),
                androidx.compose.ui.graphics.Color.Transparent,
                c.coral.content,
                onOtherServer,
                outline = c.coral.content.copy(alpha = 0.4f),
            )
        }
    }
}
