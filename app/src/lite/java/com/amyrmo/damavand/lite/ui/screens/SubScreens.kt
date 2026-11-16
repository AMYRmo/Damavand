package com.amyrmo.damavand.lite.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.components.CircleIconButton
import com.amyrmo.damavand.lite.ui.components.ChevronForwardVector
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/** Sub-screens reached from Settings (3.1.0, PattNG). */
enum class SubScreen { SUBSCRIPTIONS, ROUTING, PER_APP, LOGS, QR_SCAN }

/**
 * A sub-screen's top row: a back circle (RTL: pointing right) and
 * the screen title.
 */
@Composable
fun SubScreenHeader(titleRes: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 3.1.2: vector chevron, flipped 180° so it points back
        // (the vector auto-mirrors in RTL, so flip keeps it right).
        CircleIconButton(
            buttonSize = 40.dp,
            glyphSize = 18.dp,
            filled = false,
            onClick = onBack,
            glyphColor = LiteColors.text,
            icon = ChevronForwardVector,
            flip = true
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(titleRes),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.text,
            fontFamily = Vazirmatn
        )
    }
}
