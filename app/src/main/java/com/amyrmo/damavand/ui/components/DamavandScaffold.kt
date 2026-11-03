package com.amyrmo.damavand.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.amyrmo.damavand.ui.components.glass.GlassBackdrop
import com.amyrmo.damavand.ui.components.glass.NoGlassBackdrop
import com.amyrmo.damavand.ui.theme.LocalMotionBudget
import com.amyrmo.damavand.ui.theme.MotionDurations

/**
 * The app shell (architecture §5.1): a [TopDestination] state machine, not
 * a navigation graph. Content cross-fades between destinations
 * (AnimatedContent, Narcic pattern); the [GlassDock] floats over the
 * bottom of every destination; system back from anywhere returns Home.
 *
 * Screens are responsible for their own bottom inset (see
 * [com.amyrmo.damavand.ui.theme.Dimens.DockClearance]) so lists can scroll
 * under the dock — that scroll-under is what the dock's backdrop blur
 * samples, via [GlassBackdrop.sourceModifier] applied to the content
 * layer here.
 */
@Composable
fun DamavandScaffold(
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop = NoGlassBackdrop,
    content: @Composable (TopDestination) -> Unit,
) {
    val budget = LocalMotionBudget.current

    BackHandler(enabled = current != TopDestination.Home) {
        onSelect(TopDestination.Home)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(
                        budget.scaledDuration(MotionDurations.StandardMs),
                    ),
                ) togetherWith fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(
                        budget.scaledDuration(MotionDurations.StandardMs),
                    ),
                )
            },
            label = "destination",
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop.sourceModifier()),
        ) { destination ->
            content(destination)
        }

        GlassDock(
            current = current,
            onSelect = onSelect,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}
