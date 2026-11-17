package com.amyrmo.damavand.lite.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flag chip — the ZedSecure/Damavand structure, ported verbatim
 * (3.2.26, at Amir's order; Oblivion agrees on the shape): the
 * bundled SVG is read as bytes and handed to Coil — never a
 * file:///android_asset/ URL — 1.4:1 rectangle, 6dp radius. A
 * known country without its asset shows its 2-letter code; no
 * country at all shows "··". Never blank, never a globe.
 * (Lite has no global Coil ImageLoader like ZedSecureApp, so the
 * SvgDecoder stays on the request, as before.)
 */
@Composable
fun FlagBadge(
    countryCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp
) {
    val code = countryCode?.uppercase()
    val assetName = code?.lowercase()
    val context = LocalContext.current

    var bytes by remember(assetName) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(assetName) {
        bytes = if (assetName == null) null
        else withContext(Dispatchers.IO) {
            runCatching { context.assets.open("flags/$assetName.svg").readBytes() }.getOrNull()
        }
    }

    val shape = RoundedCornerShape(6.dp)
    val flagBytes = bytes
    when {
        flagBytes != null -> AsyncImage(
            model = remember(flagBytes) {
                ImageRequest.Builder(context)
                    .data(flagBytes)
                    .decoderFactory(SvgDecoder.Factory())
                    .build()
            },
            contentDescription = code,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(width = size * 1.4f, height = size)
                .clip(shape),
        )
        code != null -> CodeBadge(code, modifier, size, shape)
        else -> CodeBadge("··", modifier, size, shape)
    }
}

@Composable
private fun CodeBadge(
    text: String,
    modifier: Modifier,
    size: Dp,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Surface(
        color = LiteColors.surface2,
        shape = shape,
        modifier = modifier.size(width = size * 1.4f, height = size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.textSecondary,
            )
        }
    }
}
