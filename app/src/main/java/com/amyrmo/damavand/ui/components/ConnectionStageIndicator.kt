package com.amyrmo.damavand.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.ui.theme.LocalMotionBudget
import com.amyrmo.damavand.ui.theme.MotionBudget
import com.amyrmo.damavand.ui.theme.StateColors

/**
 * Session stages, mirroring the cores' own readiness vocabularies
 * (architecture §5.5): the DNS core reports these as its `WD_PROGRESS`
 * telemetry (STARTING / AUTHENTICATING / VERIFYING), and every other
 * adapter's readiness path maps onto the same four steps. `Connected` is
 * a stage of its own because the UI may only claim it after the
 * end-to-end probe passes — the v1 "Connected but no traffic" failure is
 * exactly what this indicator exists to prevent.
 */
enum class ConnectionStage {
    Starting,
    Authenticating,
    Verifying,
    Retrying,
    Connected,
    Failed,
}

/** Everything the indicator needs; produced by the session layer. */
data class StageInfo(
    val stage: ConnectionStage,
    /** e.g. "Aether — hop 2 of 3"; shown under the stepper while working. */
    val hopLabel: String? = null,
    /** Live sub-status, e.g. "scanning resolvers 12/40". */
    val subStatus: String? = null,
    /** Retry attempt count, shown when [stage] is Retrying. */
    val attempt: Int = 0,
    /** Which working stage a failure came from (for the stepper position). */
    val failedFrom: ConnectionStage = ConnectionStage.Starting,
    /** Classified failure reason, shown in the error colour. */
    val failureReason: String? = null,
)

private val WorkingSteps: List<ConnectionStage> = listOf(
    ConnectionStage.Starting,
    ConnectionStage.Authenticating,
    ConnectionStage.Verifying,
    ConnectionStage.Connected,
)

private fun ConnectionStage.stepIndex(): Int = when (this) {
    ConnectionStage.Starting -> 0
    ConnectionStage.Authenticating -> 1
    // A retry restarts the handshake: it sits on the Authenticating step.
    ConnectionStage.Retrying -> 1
    ConnectionStage.Verifying -> 2
    ConnectionStage.Connected -> 3
    ConnectionStage.Failed -> -1 // resolved via StageInfo.failedFrom
}

@Composable
private fun stageLabel(stage: ConnectionStage): String = stringResource(
    when (stage) {
        ConnectionStage.Starting -> R.string.stage_starting
        ConnectionStage.Authenticating -> R.string.stage_authenticating
        ConnectionStage.Verifying -> R.string.stage_verifying
        ConnectionStage.Retrying -> R.string.stage_retrying
        ConnectionStage.Connected -> R.string.stage_connected
        ConnectionStage.Failed -> R.string.stage_failed
    },
)

/**
 * Horizontal stepper over [WorkingSteps] with a live detail line.
 * The current step pulses only at [MotionBudget.Full] (no decorative
 * animation otherwise); Failed paints the failed step and the detail line
 * in the error colour.
 */
@Composable
fun ConnectionStageIndicator(
    info: StageInfo,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val budget = LocalMotionBudget.current
    val failed = info.stage == ConnectionStage.Failed
    val currentIndex = if (failed) info.failedFrom.stepIndex() else info.stage.stepIndex()

    val pulse = if (budget == MotionBudget.Full && !failed && info.stage != ConnectionStage.Connected) {
        val transition = rememberInfiniteTransition(label = "stage-pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "stage-pulse-alpha",
        )
        alpha
    } else {
        1f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkingSteps.forEachIndexed { index, step ->
                val done = index < currentIndex || info.stage == ConnectionStage.Connected
                val current = index == currentIndex
                val dotColor = when {
                    failed && current -> StateColors.Error
                    done -> StateColors.Connected
                    current -> scheme.primary.copy(alpha = pulse)
                    else -> scheme.outlineVariant
                }
                Box(
                    modifier = Modifier
                        .size(if (current) 13.dp else 10.dp)
                        .clip(CircleShape)
                        .then(
                            if (done || current) {
                                Modifier.background(dotColor)
                            } else {
                                Modifier.border(1.5.dp, scheme.outline, CircleShape)
                            },
                        ),
                )
                if (index < WorkingSteps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < currentIndex || info.stage == ConnectionStage.Connected) {
                                    StateColors.Connected
                                } else {
                                    scheme.outlineVariant
                                },
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            WorkingSteps.forEachIndexed { index, step ->
                Text(
                    text = when {
                        failed && index == currentIndex -> stageLabel(ConnectionStage.Failed)
                        info.stage == ConnectionStage.Retrying && index == currentIndex ->
                            "${stageLabel(ConnectionStage.Retrying)} (${info.attempt})"

                        else -> stageLabel(step)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        failed && index == currentIndex -> StateColors.Error
                        index == currentIndex -> scheme.onSurface
                        index < currentIndex || info.stage == ConnectionStage.Connected -> StateColors.Connected
                        else -> scheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val detail = when {
            failed -> info.failureReason ?: info.subStatus
            else -> info.subStatus ?: info.hopLabel
        }
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) StateColors.Error else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
