package com.amyrmo.damavand.ui.settings

// Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the
// owner's own fork lineage of that codebase.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.data.settings.AppDesign
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind

/**
 * Home-design picker (ported from v1 Damavand's AppDesignPicker and
 * extended to the four v2 designs): a 2×2 grid of selectable preview
 * cards, each a small drawn preview in theme colours with its label
 * underneath. The selected card carries a primary border and a check
 * badge.
 */
@Composable
fun AppDesignPicker(
    current: AppDesign,
    onPick: (AppDesign) -> Unit,
    modifier: Modifier = Modifier,
) {
    val designs = listOf(
        AppDesign.PORTAL to stringResource(R.string.design_portal),
        AppDesign.BENTO to stringResource(R.string.design_bento),
        AppDesign.PIXEL to stringResource(R.string.design_pixel),
        AppDesign.CLASSIC to stringResource(R.string.design_classic),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        designs.chunked(2).forEach { rowDesigns ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowDesigns.forEach { (design, label) ->
                    DesignCard(
                        label = label,
                        selected = design == current,
                        onClick = { onPick(design) },
                        modifier = Modifier.weight(1f),
                    ) {
                        when (design) {
                            AppDesign.PORTAL -> PortalPreview()
                            AppDesign.BENTO -> BentoPreview()
                            AppDesign.PIXEL -> PixelPreview()
                            AppDesign.CLASSIC -> ClassicPreview()
                        }
                    }
                }
                if (rowDesigns.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DesignCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(shape)
                .background(scheme.surfaceContainerLow)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outlineVariant,
                    shape = shape,
                )
                .clickable(onClick = onClick),
        ) {
            preview()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    DamavandIcon(
                        kind = DamavandIconKind.Check,
                        tint = scheme.onPrimary,
                        size = 14.dp,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

/** Portal: a centred door rectangle over a caption line (v1's home). */
@Composable
private fun PortalPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 52.dp)
                .clip(RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 5.dp)
                .clip(CircleShape)
                .background(scheme.onSurface.copy(alpha = 0.55f)),
        )
    }
}

/** Bento: a 2×2 tile grid (ZedSecure v3.1.8's default home). */
@Composable
private fun BentoPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.primaryContainer),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceContainerHighest),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceContainerHighest),
            )
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.secondaryContainer),
            )
        }
    }
}

/** Pixel: a hero block with a centred disc over a pill (ZedSecure Pixel). */
@Composable
private fun PixelPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest),
        )
    }
}

/** Classic: plain list rows (the previous v2 home). */
@Composable
private fun ClassicPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (index == 0) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == 0) scheme.primary else scheme.onSurfaceVariant,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurface.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
