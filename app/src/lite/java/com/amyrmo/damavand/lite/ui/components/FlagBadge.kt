package com.amyrmo.damavand.lite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flag chip (spec §4.4 / §5.3): real country flag image from the bundled
 * assets (assets/flags/<lowercase-code>.svg), 44x44, radius 14, no emoji.
 * Unknown country: a neutral surface-2 chip with a 20px globe line-icon.
 * Never a letter monogram, never a coloured placeholder.
 */
@Composable
fun FlagBadge(
    countryCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val code = countryCode?.trim()?.lowercase()?.takeIf { it.length == 2 }
    val context = LocalContext.current
    var exists by remember(code) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(code) {
        exists = if (code == null) {
            false
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("flags/$code.svg").close()
                    true
                }.getOrDefault(false)
            }
        }
    }

    val shape = RoundedCornerShape(14.dp)
    when (exists) {
        true -> {
            val model = remember(code) {
                ImageRequest.Builder(context)
                    .data("file:///android_asset/flags/$code.svg")
                    .decoderFactory(SvgDecoder.Factory())
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = code,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(shape)
            )
        }
        else -> {
            // Unknown country (or flag still loading): globe chip.
            Box(
                modifier = modifier
                    .size(size)
                    .background(LiteColors.surface2, shape),
                contentAlignment = Alignment.Center
            ) {
                GlobeLineIcon(size = 20.dp, color = LiteColors.textMuted)
            }
        }
    }
}
