/*
 * Ported from Damavand v1 (the owner's own app, itself a ZedSecure fork).
 *
 * Faithful port of v1's Portal home (PortalHome.kt, 1139 lines, KMP) to
 * v2's Android-only Compose and [SessionState] model. Structure and
 * composable names follow v1: PortalTopBar / PortalStage / PortalDoor /
 * PortalCaption / PortalTicket / rememberPortalClock.
 *
 * Adaptations (also flagged inline where they happen):
 *  - KMP `Res` resources → androidx `stringResource` with existing v2 keys.
 *  - MaterialShapes expressive cookie morphs → standard
 *    RoundedCornerShape / CircleShape with animated corners.
 *  - v1's WallOfLight particle field → two breathing glow rings around
 *    the door (the door/stage metaphor and its state colours are kept).
 *  - v1's ConnectionUiState/NetworkInfo → SessionState + selected
 *    Profile + vm.geo / vm.traffic.
 *  - v1's FlowMeter → v2's DashboardCard (same live rates/totals data).
 *  - v1's unlock opened a hidden surface; here it reveals the IpDetails
 *    panel + speed-test card section (see PortalHome).
 */
package com.amyrmo.damavand.ui.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.core.session.SessionState
import com.amyrmo.damavand.data.profiles.ProfileFactory
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.platform.GeoInfo
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.components.ConnectionStageIndicator
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind
import com.amyrmo.damavand.ui.formatDuration
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.StateColors
import com.amyrmo.damavand.ui.theme.accent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val PORTAL_TAPS_TO_UNLOCK = 5
private const val PORTAL_TAP_WINDOW_MS = 1_200L

/**
 * The Portal home: top bar (status pill + clock + the 5-tap unlock) →
 * fault banner → the door stage → caption → ticket, with the connected
 * dashboard under the caption and the unlocked IP/speed section at the
 * bottom. Tapping the door toggles connect/disconnect, exactly as in v1.
 */
@Composable
fun PortalHome(
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
    val storeFault by vm.storeFault.collectAsStateWithLifecycle()

    val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val clock = rememberPortalClock(running = true)
    var unlocked by remember { mutableStateOf(false) }

    val onToggle: () -> Unit = {
        when (session) {
            is SessionState.Connected, is SessionState.Connecting -> vm.disconnect()
            else -> profile?.let(onConnect)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 640.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = Dimens.HomePadding,
                        end = Dimens.HomePadding,
                        top = 16.dp,
                        bottom = Dimens.DockClearance + 16.dp,
                    ),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PortalTopBar(
                state = session,
                clock = clock.value,
                onSecretUnlocked = { unlocked = !unlocked },
            )

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
                PortalStage(
                    state = session,
                    onTap = onToggle,
                )
                PortalCaption(
                    state = session,
                    compact = compact,
                    // v1 tapped the caption to cycle a status detail; v2 has
                    // no such surface, so the tap is a deliberate no-op.
                    onStatusTap = {},
                )

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

                PortalTicket(
                    profile = profile,
                    geo = geo,
                    connected = session is SessionState.Connected,
                    onClick = onOpenServers,
                )

                val subscription = profile.subscriptionId?.let { subId ->
                    subscriptions.firstOrNull { it.id == subId }
                }
                if (subscription != null) {
                    SubscriptionHealthCard(
                        subscription = subscription,
                        onClick = onOpenSubscriptions,
                    )
                }

                // THE UNLOCK (v1 behaviour e): 5 taps within 1200 ms on the
                // top bar toggles this section — IP details + speed test.
                AnimatedVisibility(visible = unlocked) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        IpDetailsPanel(
                            geo = geo,
                            socksPort = (session as? SessionState.Connected)?.localSocksPort,
                        )
                        SpeedTestCard()
                    }
                }
            }
        }
    }
}

/**
 * Wall clock for the top bar (v1 behaviour d): "HH:mm", refreshed every
 * second while [running]. (v1's same-named helper returned an animation
 * clock in float seconds for its particle wall; the wall is simplified
 * away in v2, so this returns the displayed time directly.)
 */
@Composable
fun rememberPortalClock(running: Boolean): State<String> {
    val time = remember { mutableStateOf("--:--") }
    LaunchedEffect(running) {
        val format = SimpleDateFormat("HH:mm", Locale.US)
        time.value = format.format(Date())
        if (!running) return@LaunchedEffect
        while (true) {
            delay(1_000)
            time.value = format.format(Date())
        }
    }
    return time
}

@Composable
fun PortalTopBar(
    state: SessionState,
    clock: String,
    onSecretUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(state = state)
        Spacer(Modifier.weight(1f))
        // The unlock tap target (v1 behaviour e): the app-name/clock area.
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < PORTAL_TAP_WINDOW_MS) taps + 1 else 1
                    lastTap = now
                    if (taps >= PORTAL_TAPS_TO_UNLOCK) {
                        taps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSecretUnlocked()
                    } else if (taps > 1) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = clock,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** v1's status pill, kept file-private with the same name as in v1. */
@Composable
private fun StatusPill(state: SessionState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (dotColor, label) = when (state) {
        is SessionState.Connected -> StateColors.Connected to stringResource(R.string.home_status_connected)
        is SessionState.Connecting -> StateColors.Connecting to stringResource(R.string.home_status_connecting)
        is SessionState.Failed -> StateColors.Error to stringResource(R.string.home_status_failed)
        SessionState.Idle -> scheme.outline to stringResource(R.string.home_status_idle)
    }
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * The stage: glow rings + the door, centred (v1 behaviour a). v1's
 * WallOfLight Canvas particle field is simplified to two state-tinted
 * rings whose alpha breathes with the session state — the door remains
 * the single interactive surface.
 */
@Composable
fun PortalStage(
    state: SessionState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glow by animateColorAsState(
        targetValue = when (state) {
            is SessionState.Connected -> StateColors.Connected
            is SessionState.Connecting -> StateColors.Connecting
            is SessionState.Failed -> StateColors.Error
            SessionState.Idle -> scheme.outlineVariant
        },
        animationSpec = tween(700),
        label = "portal-glow",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = when (state) {
            is SessionState.Connected -> 0.55f
            is SessionState.Connecting -> 0.45f
            is SessionState.Failed -> 0.40f
            SessionState.Idle -> 0.18f
        },
        animationSpec = tween(700),
        label = "portal-glow-alpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(272.dp)
                .clip(CircleShape)
                .border(1.5.dp, glow.copy(alpha = glowAlpha * 0.6f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(232.dp)
                .clip(CircleShape)
                .border(1.5.dp, glow.copy(alpha = glowAlpha), CircleShape),
        )
        PortalDoor(
            state = state,
            doorSize = 196.dp,
            onTap = onTap,
        )
    }
}

/**
 * The door (v1 behaviour a): a large central surface whose shape, fill
 * and centre content follow the session state — rounded "sealed" door
 * when idle, primary fill while connecting, circle showing the session
 * duration when connected, error surface (with v1's shake) when failed.
 * A tap toggles connect/disconnect. v1 morphed MaterialShapes cookie
 * polygons; v2 animates a RoundedCornerShape's radius instead.
 */
@Composable
fun PortalDoor(
    state: SessionState,
    doorSize: Dp,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    val shake = remember { Animatable(0f) }
    LaunchedEffect(state is SessionState.Failed) {
        if (state !is SessionState.Failed) return@LaunchedEffect
        for (x in listOf(14f, -11f, 8f, -5f, 2f, 0f)) shake.animateTo(x, tween(55))
    }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(120),
        label = "door-squash",
    )
    val corner by animateDpAsState(
        targetValue = when (state) {
            is SessionState.Connected -> doorSize / 2
            is SessionState.Connecting -> 52.dp
            is SessionState.Failed -> 24.dp
            SessionState.Idle -> 40.dp
        },
        animationSpec = tween(500),
        label = "door-corner",
    )
    val doorShape = RoundedCornerShape(corner)
    val fillTop by animateColorAsState(
        targetValue = when (state) {
            is SessionState.Connected -> scheme.primaryContainer
            is SessionState.Connecting -> scheme.primary
            is SessionState.Failed -> scheme.errorContainer
            SessionState.Idle -> scheme.surfaceContainerHighest
        },
        animationSpec = tween(700),
        label = "door-fill-top",
    )
    val fillBottom by animateColorAsState(
        targetValue = when (state) {
            is SessionState.Connected -> scheme.primary
            is SessionState.Connecting -> scheme.tertiary
            is SessionState.Failed -> scheme.errorContainer
            SessionState.Idle -> scheme.surfaceContainerHighest
        },
        animationSpec = tween(700),
        label = "door-fill-bottom",
    )
    val rim by animateColorAsState(
        targetValue = when (state) {
            is SessionState.Failed -> StateColors.Error
            SessionState.Idle -> scheme.outlineVariant
            else -> Color.Transparent
        },
        animationSpec = tween(700),
        label = "door-rim",
    )
    val ink by animateColorAsState(
        targetValue = when (state) {
            is SessionState.Failed -> scheme.onErrorContainer
            is SessionState.Connecting -> scheme.onPrimary
            is SessionState.Connected -> scheme.onPrimary
            SessionState.Idle -> scheme.onSurface
        },
        animationSpec = tween(700),
        label = "door-ink",
    )
    val actionLabel = stringResource(
        when (state) {
            is SessionState.Connected -> R.string.disconnect
            is SessionState.Connecting -> R.string.connecting
            is SessionState.Failed -> R.string.connect
            SessionState.Idle -> R.string.connect
        },
    )

    // Session duration for the connected centre (same ticker pattern as
    // DashboardCard in HomeCards.kt).
    val connected = state as? SessionState.Connected
    var now by remember(connected?.sessionId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(connected?.sessionId) {
        if (connected == null) return@LaunchedEffect
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    Box(
        modifier = modifier
            .size(doorSize)
            .graphicsLayer {
                translationX = shake.value
                scaleX = squash
                scaleY = squash
            }
            .semantics { contentDescription = actionLabel }
            .clip(doorShape)
            .background(Brush.radialGradient(listOf(fillTop, fillBottom)))
            .border(1.6.dp, rim, doorShape)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "door-center",
        ) { s ->
            when (s) {
                is SessionState.Connected -> Text(
                    text = formatDuration(now - s.startedAtElapsedMs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                )
                is SessionState.Connecting -> CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = ink,
                    strokeWidth = 3.dp,
                )
                else -> PortalPowerGlyph(tint = ink, size = doorSize * 0.36f)
            }
        }
    }
}

/** Power glyph drawn with Canvas primitives (v2 carries no icon font). */
@Composable
private fun PortalPowerGlyph(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = this.size.minDimension / 2f * 0.78f
        val stroke = this.size.minDimension * 0.09f
        drawArc(
            color = tint,
            startAngle = -50f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(center.x, center.y - radius * 1.18f),
            end = Offset(center.x, center.y - radius * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** The caption under the door (v1 behaviour b): animated status headline. */
@Composable
fun PortalCaption(
    state: SessionState,
    compact: Boolean,
    onStatusTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(180)) },
        label = "portal-caption",
        modifier = modifier,
    ) { s ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val headline = if (compact) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.displaySmall
            }
            Text(
                text = stringResource(
                    when (s) {
                        is SessionState.Connected -> R.string.home_status_connected
                        is SessionState.Connecting -> R.string.home_status_connecting
                        is SessionState.Failed -> R.string.home_status_failed
                        SessionState.Idle -> R.string.home_status_idle
                    },
                ),
                style = if (s is SessionState.Connected) {
                    headline.copy(
                        brush = Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)),
                    )
                } else {
                    headline
                },
                color = if (s is SessionState.Failed) scheme.error else scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStatusTap,
                ),
            )
            if (s is SessionState.Idle) {
                Spacer(Modifier.height(10.dp))
                Text(
                    // TODO string key: v1's portal_hint has no v2 equivalent yet.
                    text = "Tap the door to connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The ticket (v1 behaviour c): the selected profile on a primaryContainer
 * card; tap goes to Servers. When connected it also carries the exit
 * IP/location from [geo], like v1's ticket detail line did with its
 * NetworkInfo.
 */
@Composable
fun PortalTicket(
    profile: Profile,
    geo: GeoInfo?,
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 18.dp else 30.dp,
        animationSpec = tween(160),
        label = "ticket-corner",
    )
    val endpoint = ProfileFactory.endpointOf(profile)
    val detail = buildString {
        append(ProfileFactory.protocolLabel(profile))
        if (endpoint != null) append(" · ${endpoint.first}:${endpoint.second}")
    }
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(corner),
        color = scheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(profile.kind.accent()),
                contentAlignment = Alignment.Center,
            ) {
                DamavandIcon(
                    kind = DamavandIconKind.Servers,
                    tint = Color.White,
                    size = 22.dp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (connected) {
                    Text(
                        text = "${stringResource(R.string.home_exit_ip)}: ${geo?.ip ?: "…"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${stringResource(R.string.home_location)}: " +
                            listOfNotNull(geo?.city, geo?.country)
                                .joinToString(", ")
                                .ifBlank { "…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(scheme.onPrimaryContainer.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                DamavandIcon(
                    kind = DamavandIconKind.ChevronRight,
                    tint = scheme.onPrimaryContainer,
                    size = 20.dp,
                )
            }
        }
    }
}
