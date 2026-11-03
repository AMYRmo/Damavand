package com.amyrmo.damavand.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.components.ConnectPill
import com.amyrmo.damavand.ui.components.ConnectPillState
import com.amyrmo.damavand.ui.components.ConnectionStage
import com.amyrmo.damavand.ui.components.ConnectionStageIndicator
import com.amyrmo.damavand.ui.components.DamavandScaffold
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.GlassCard
import com.amyrmo.damavand.ui.components.ProfileRow
import com.amyrmo.damavand.ui.components.SectionHeader
import com.amyrmo.damavand.ui.components.StageInfo
import com.amyrmo.damavand.ui.components.TopDestination
import com.amyrmo.damavand.ui.components.glass.NoGlassBackdrop
import com.amyrmo.damavand.ui.components.glass.rememberHazeGlassBackdrop
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.LocalGlassCapabilities
import com.amyrmo.damavand.ui.theme.StateColors
import com.amyrmo.damavand.ui.theme.accent

/**
 * Phase-1 home placeholder: NOT the real Home screen (that is the screens
 * phase) — a living showcase that wires the whole design system together
 * so MainActivity demonstrates the kit instead of a text label:
 * DamavandScaffold + GlassDock over scrolling content (the scroll-under is
 * what the dock's backdrop blur samples), the GlassCard, the ConnectPill
 * cycling Idle → Connecting → Connected with the ConnectionStageIndicator
 * following it, one ProfileRow per core accent, and a FormTextField.
 *
 * The dock backdrop follows the capability ladder: the Haze backdrop is
 * created unconditionally but only *selected* when
 * [LocalGlassCapabilities] reports real blur; otherwise the dock renders
 * its identical fake-glass rung via [NoGlassBackdrop].
 */
@Composable
fun HomePlaceholder() {
    var destination by rememberSaveable { mutableStateOf(TopDestination.Home) }
    val capabilities = LocalGlassCapabilities.current
    val hazeBackdrop = rememberHazeGlassBackdrop()
    val backdrop = if (capabilities.realBlur) hazeBackdrop else NoGlassBackdrop

    DamavandScaffold(
        current = destination,
        onSelect = { destination = it },
        backdrop = backdrop,
    ) { dest ->
        when (dest) {
            TopDestination.Home -> DesignSystemDemo()
            else -> DestinationPlaceholder(dest)
        }
    }
}

@Composable
private fun DesignSystemDemo() {
    var pillState by rememberSaveable { mutableStateOf(ConnectPillState.Idle) }
    var sessionId by rememberSaveable { mutableIntStateOf(0) }
    var activeCore by rememberSaveable { mutableStateOf(CoreKind.XRAY) }
    var endpoint by rememberSaveable { mutableStateOf("") }

    val stageInfo = when (pillState) {
        ConnectPillState.Idle ->
            StageInfo(ConnectionStage.Starting, subStatus = "Design-system demo — no live session")

        ConnectPillState.Connecting ->
            StageInfo(
                ConnectionStage.Authenticating,
                hopLabel = "Aether — hop 1 of 1",
                subStatus = "Scanning resolvers 12/40",
            )

        ConnectPillState.Connected ->
            StageInfo(ConnectionStage.Connected, subStatus = "Exit verified end-to-end")

        ConnectPillState.Failed ->
            StageInfo(
                ConnectionStage.Failed,
                failedFrom = ConnectionStage.Verifying,
                failureReason = "Probe failed: no traffic through the tunnel",
            )
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
        // Brand header (the real BrandHeader lands with the Home screen).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Damavand 🗿",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when (pillState) {
                    ConnectPillState.Connected -> "● Connected"
                    ConnectPillState.Connecting -> "● Connecting"
                    ConnectPillState.Failed -> "● Failed"
                    ConnectPillState.Idle -> "○ Idle"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when (pillState) {
                    ConnectPillState.Connected -> StateColors.Connected
                    ConnectPillState.Connecting -> StateColors.Connecting
                    ConnectPillState.Failed -> StateColors.Error
                    ConnectPillState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        SectionHeader(title = "Connection")
        GlassCard {
            ProfileRow(
                name = "Demo profile — ${activeCore.name.lowercase()}",
                coreKind = activeCore,
                subtitle = "Tap a core below to switch the accent rail",
                active = pillState == ConnectPillState.Connected,
                onClick = {},
            )
            Spacer(Modifier.height(14.dp))
            ConnectionStageIndicator(info = stageInfo)
        }
        ConnectPill(
            state = pillState,
            sessionId = sessionId,
            onClick = {
                pillState = when (pillState) {
                    ConnectPillState.Idle -> ConnectPillState.Connecting
                    ConnectPillState.Connecting -> {
                        sessionId += 1
                        ConnectPillState.Connected
                    }

                    ConnectPillState.Connected -> ConnectPillState.Failed
                    ConnectPillState.Failed -> ConnectPillState.Idle
                }
            },
        )

        SectionHeader(title = "Cores")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CoreKind.XRAY,
                CoreKind.PSIPHON,
                CoreKind.AETHER,
                CoreKind.MIHOMO,
                CoreKind.WHITEDNS,
            ).forEach { kind ->
                ProfileRow(
                    name = kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    coreKind = kind,
                    subtitle = "Accent ${kind.accent()}",
                    active = kind == activeCore,
                    onClick = { activeCore = kind },
                )
            }
        }

        SectionHeader(title = "Editor kit", accent = activeCore.accent())
        FormTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = "Endpoint",
            placeholder = "engage.cloudflareclient.com:2408",
            supportingText = "FormTextField — onValueChange is a required parameter",
            forceLtr = true,
        )
    }
}

@Composable
private fun DestinationPlaceholder(destination: TopDestination) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = destination.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Screen lands in the screens phase — the shell, dock and theme are the Phase-1 deliverable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
