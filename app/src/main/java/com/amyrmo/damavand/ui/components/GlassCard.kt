package com.amyrmo.damavand.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.ui.components.glass.SpecularStrip
import com.amyrmo.damavand.ui.theme.DamavandShapeTokens
import com.amyrmo.damavand.ui.theme.Dimens
import com.amyrmo.damavand.ui.theme.LocalGlassCapabilities
import com.amyrmo.damavand.ui.theme.cardBorderBrush

/**
 * The home connection card (design-research §4.4 `ActiveConfigCard` /
 * `DashboardCard` surface).
 *
 * Deliberately NOT true glass: status/dashboard cards carry live text
 * (ping, IP, traffic) that must keep full contrast, so the spec puts them
 * on the "near-opaque solid (92–97 %) + 0.5 dp border" rung. This card is
 * that rung, styled to sit in the same family as the glass control layer:
 * [MaterialTheme.colorScheme.surfaceContainer] at 95 % alpha, a quiet
 * vertical-gradient border, a faint specular strip. Under the
 * reduce-transparency capability it goes fully opaque.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val reduceTransparency = LocalGlassCapabilities.current.reduceTransparency
    val fill = scheme.surfaceContainer.copy(alpha = if (reduceTransparency) 1f else 0.95f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(DamavandShapeTokens.Card)
            .background(fill)
            .border(
                width = 1.dp,
                brush = cardBorderBrush(scheme.onSurface),
                shape = DamavandShapeTokens.Card,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        SpecularStrip(shape = DamavandShapeTokens.Card, peakAlpha = 0.07f, height = 18.dp)
        Column(
            modifier = Modifier.padding(Dimens.CardPadding),
            content = content,
        )
    }
}
