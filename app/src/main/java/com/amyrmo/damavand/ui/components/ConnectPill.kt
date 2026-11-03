package com.amyrmo.damavand.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.ui.components.glass.SpecularStrip
import com.amyrmo.damavand.ui.theme.Brand
import com.amyrmo.damavand.ui.theme.DamavandShapeTokens
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.GlassTokens
import com.amyrmo.damavand.ui.theme.Gradients
import com.amyrmo.damavand.ui.theme.LocalMotionBudget
import com.amyrmo.damavand.ui.theme.MotionBudget
import com.amyrmo.damavand.ui.theme.MotionDurations
import com.amyrmo.damavand.ui.theme.StateColors

/** Visual state of the connect control. Session logic maps its own state
 *  machine onto these four; the pill itself is dumb (design-research §4.4). */
enum class ConnectPillState {
    Idle,
    Connecting,
    Connected,
    Failed,
}

/**
 * The connect pill — the only *tinted* element in the app (Apple's rule:
 * tint only the primary action; architecture §5.3).
 *
 * Fake glass by design: a state gradient fill (idle = primary → primary
 * @78 %, connecting = [Gradients.Connecting], connected =
 * [Gradients.connectedForSession] at state-legible opacity, failed = the
 * error colour), a white tint overlay at [GlassTokens.PillTintAlpha], a
 * gradient rim, a specular strip, and a coloured glow shadow while
 * connected. Real refraction on the rim band (Haze Glass, API 33+) is a
 * Phase-7 trial behind the Appearance setting — deliberately not wired
 * here yet. State-colour changes cross-fade over ~900 ms via
 * [LocalMotionBudget]; press scale is 0.97, suppressed when Paused.
 */
@Composable
fun ConnectPill(
    state: ConnectPillState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sessionId: Int = 0,
    label: String? = null,
    enabled: Boolean = true,
) {
    val budget = LocalMotionBudget.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = budget.pressScale(pressed && enabled),
        animationSpec = if (budget == MotionBudget.Paused) snap() else tween(120),
        label = "pill-press",
    )

    val glowColor = when (state) {
        ConnectPillState.Connected -> StateColors.Connected
        ConnectPillState.Failed -> StateColors.Error
        else -> Color.Transparent
    }
    val resolvedLabel = label ?: when (state) {
        ConnectPillState.Idle -> stringResource(R.string.connect)
        ConnectPillState.Connecting -> stringResource(R.string.connecting)
        ConnectPillState.Connected -> stringResource(R.string.disconnect)
        ConnectPillState.Failed -> stringResource(R.string.connection_failed)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.PillHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (state == ConnectPillState.Connected || state == ConnectPillState.Failed) 18.dp else 8.dp,
                shape = DamavandShapeTokens.Capsule,
                ambientColor = glowColor.copy(alpha = 0.35f),
                spotColor = glowColor.copy(alpha = 0.55f),
            )
            .clip(DamavandShapeTokens.Capsule)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        // State fill, cross-faded (~900 ms state-colour rule).
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(budget.scaledDuration(MotionDurations.StateColorMs))) togetherWith
                    fadeOut(tween(budget.scaledDuration(MotionDurations.StateColorMs)))
            },
            label = "pill-state",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(fillColors(s, sessionId))),
            )
        }
        // Tint overlay + specular + rim (the fake-glass layers).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = GlassTokens.PillTintAlpha)),
        )
        SpecularStrip(shape = DamavandShapeTokens.Capsule, peakAlpha = 0.20f, height = 26.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.20f),
                        ),
                    ),
                    shape = DamavandShapeTokens.Capsule,
                ),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == ConnectPillState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                )
                Box(Modifier.size(10.dp))
            }
            Text(
                text = resolvedLabel,
                // titleLarge, emphasized — same scale point as
                // DamavandTextStyles.pillLabel, but follows whichever font
                // family the theme installed (Inter / Vazirmatn).
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
                color = Color.White,
            )
        }
    }
}

private fun fillColors(state: ConnectPillState, sessionId: Int): List<Color> = when (state) {
    ConnectPillState.Idle ->
        listOf(Brand.Primary, Brand.Primary.copy(alpha = 0.78f))

    ConnectPillState.Connecting -> Gradients.Connecting
    ConnectPillState.Connected -> Gradients.connectedForSession(sessionId)
    ConnectPillState.Failed ->
        listOf(StateColors.Error, StateColors.Error.copy(alpha = 0.80f))
}
