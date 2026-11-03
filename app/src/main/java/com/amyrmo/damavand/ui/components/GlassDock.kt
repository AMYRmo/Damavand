package com.amyrmo.damavand.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.ui.components.glass.GlassBackdrop
import com.amyrmo.damavand.ui.components.glass.NoGlassBackdrop
import com.amyrmo.damavand.ui.components.glass.SpecularStrip
import com.amyrmo.damavand.ui.theme.DamavandShapeTokens
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.LocalMotionBudget
import com.amyrmo.damavand.ui.theme.MotionBudget

/**
 * The floating bottom navigation dock — the ONE true-glass surface in the
 * app (architecture §5.3; design-research §3.4).
 *
 * Spec: capsule, 64 dp high, 16 dp side / 12 dp bottom margins; selected
 * item = accent capsule (gradient 30 % → 14 %) with its own rim + specular
 * strip; shadow under the capsule. Rendering follows the fallback ladder
 * through [backdrop]: real backdrop blur when the [GlassBackdrop]
 * implementation + capabilities allow it, otherwise the identical
 * silhouette in Narcic's fake glass (see glass/FakeGlass.kt).
 *
 * The sliding indicator, spring feel and click-consumption trick are
 * adapted from Narcic-Getway's `IosInteractiveTabBar`; the indicator
 * honours [LocalMotionBudget] (Paused = snap, no spring).
 */
@Composable
fun GlassDock(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop = NoGlassBackdrop,
    destinations: List<TopDestination> = TopDestination.entries,
) {
    val haptics = LocalHapticFeedback.current
    val budget = LocalMotionBudget.current
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    val accent = scheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.DockSideMargin, end = Dimens.DockSideMargin, bottom = Dimens.DockBottomMargin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(Dimens.DockHeight)
                .shadow(
                    elevation = if (isDark) 16.dp else 10.dp,
                    shape = DamavandShapeTokens.Capsule,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.12f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.75f else 0.20f),
                ),
        ) {
            backdrop.BlurSurface(
                shape = DamavandShapeTokens.Capsule,
                isDark = isDark,
                modifier = Modifier
                    .fillMaxSize()
                    // Consume taps on the dock body so nothing leaks to the
                    // content scrolling underneath (Narcic trick).
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                DockItems(
                    destinations = destinations,
                    current = current,
                    accent = accent,
                    isDark = isDark,
                    budget = budget,
                    onSelect = { dest ->
                        if (dest != current) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(dest)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DockItems(
    destinations: List<TopDestination>,
    current: TopDestination,
    accent: Color,
    isDark: Boolean,
    budget: MotionBudget,
    onSelect: (TopDestination) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val tabCount = destinations.size.coerceAtLeast(1)
        val tabWidth = maxWidth / tabCount
        val selectedIndex = destinations.indexOf(current).coerceAtLeast(0)

        val animatedSlot by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = if (budget == MotionBudget.Paused) {
                snap()
            } else {
                spring(dampingRatio = 0.76f, stiffness = 380f)
            },
            label = "dock-slot",
        )
        // Direction-aware indicator (fa-crash audit): the item row lays out
        // start→end, so in RTL slot 0 sits at the RIGHT edge; a physical
        // left offset would highlight the wrong tab. Mirror the offset.
        val layoutDirection = LocalLayoutDirection.current
        val indicatorX = if (layoutDirection == LayoutDirection.Rtl) {
            maxWidth - (tabWidth * (animatedSlot + 1f)) + 5.dp
        } else {
            (tabWidth * animatedSlot) + 5.dp
        }
        val indicatorWidth = (tabWidth - 10.dp).coerceAtLeast(10.dp)
        val indicatorShape = RoundedCornerShape(26.dp)

        // Sliding selected-item capsule: accent gradient 30 % → 14 %, rim,
        // specular strip (design-research §3.4).
        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorX.roundToPx(), 6.dp.roundToPx()) }
                .width(indicatorWidth)
                .height(52.dp)
                .clip(indicatorShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = if (isDark) 0.30f else 0.20f),
                            accent.copy(alpha = if (isDark) 0.14f else 0.08f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = if (isDark) 0.75f else 0.60f),
                            accent.copy(alpha = if (isDark) 0.20f else 0.12f),
                        ),
                    ),
                    shape = indicatorShape,
                ),
        ) {
            SpecularStrip(shape = indicatorShape, peakAlpha = if (isDark) 0.22f else 0.40f, height = 18.dp)
        }

        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            destinations.forEach { dest ->
                val selected = dest == current
                val label = stringResource(dest.labelRes)
                val tint by animateColorAsState(
                    targetValue = when {
                        selected -> accent
                        isDark -> com.amyrmo.damavand.ui.theme.DockColors.InactiveDark
                        else -> com.amyrmo.damavand.ui.theme.DockColors.InactiveLight
                    },
                    animationSpec = tween(budget.scaledDuration(200)),
                    label = "dock-tint-${dest.name}",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = if (budget == MotionBudget.Paused) snap() else spring(dampingRatio = 0.7f, stiffness = 420f),
                    label = "dock-scale-${dest.name}",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(dest) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    ) {
                        DamavandIcon(kind = dest.icon, tint = tint, size = 22.dp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
