package com.amyrmo.damavand.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.amyrmo.damavand.ui.theme.DamavandTheme

/**
 * Design-system previews. They live in the debug source set because the
 * catalog wires ui-tooling / ui-tooling-preview as debugImplementation —
 * @Preview references in main would break the release compile.
 */
@Preview(showBackground = true, name = "Design system — dark")
@Composable
private fun HomePlaceholderDarkPreview() {
    DamavandTheme(darkTheme = true) {
        HomePlaceholder()
    }
}

@Preview(showBackground = true, name = "Design system — light")
@Composable
private fun HomePlaceholderLightPreview() {
    DamavandTheme(darkTheme = false) {
        HomePlaceholder()
    }
}

@Preview(showBackground = true, name = "Design system — AMOLED")
@Composable
private fun HomePlaceholderAmoledPreview() {
    DamavandTheme(darkTheme = true, amoled = true) {
        HomePlaceholder()
    }
}

@Preview(showBackground = true, name = "Design system — Persian dark")
@Composable
private fun HomePlaceholderPersianPreview() {
    DamavandTheme(darkTheme = true, persian = true) {
        HomePlaceholder()
    }
}
