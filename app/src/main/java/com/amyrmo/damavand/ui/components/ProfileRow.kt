package com.amyrmo.damavand.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.model.CoreKind
import com.amyrmo.damavand.ui.theme.DamavandShapeTokens
import com.amyrmo.damavand.ui.theme.accent

/**
 * Skeleton list row for a profile (design-research §4.4 `ServerCard`,
 * reduced to the shared frame): per-core accent rail, name, subtitle, and
 * a trailing slot (ping chip / expiry badge / overflow). Rows are SOLID —
 * list rows are on the never-glass list (architecture §5.3) — with the
 * active profile shown by a primary-container fill.
 *
 * This is the frame the Servers phase fleshes out (flag, badges, overflow
 * menu); it is intentionally profile-model-agnostic: callers pass display
 * strings, so chains and auto rows can reuse the same frame.
 */
@Composable
fun ProfileRow(
    name: String,
    coreKind: CoreKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    active: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DamavandShapeTokens.Card)
            .background(
                if (active) {
                    scheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    scheme.surfaceContainerLow
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(coreKind.accent()),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = if (active) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
