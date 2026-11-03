package com.amyrmo.damavand.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * The one text field every editor uses (architecture §§5.2, 6.3).
 *
 * [onValueChange] is a REQUIRED parameter with no default — deliberately.
 * In v1, `AetherSheet`'s field composable defaulted its change handler to
 * a no-op and none of its 20 call sites passed one, so every Aether text
 * field silently swallowed keystrokes (build #75). With no default here,
 * that bug is a compile error, not a field report.
 *
 * [forceLtr] wraps the field in an LTR layout direction for values that
 * are not prose — endpoints, commands, URLs, keys (§5.4: LTR-only
 * artefacts stay LTR inside an RTL UI).
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    forceLtr: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
) {
    val field: @Composable () -> Unit = {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = if (placeholder != null) {
                { Text(placeholder) }
            } else {
                null
            },
            supportingText = if (supportingText != null) {
                { Text(supportingText) }
            } else {
                null
            },
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            keyboardOptions = keyboardOptions,
            modifier = modifier.fillMaxWidth(),
        )
    }
    if (forceLtr) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            field()
        }
    } else {
        field()
    }
}
