package com.amyrmo.damavand.lite.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---- Extra line icons (3.1.0, PattNG) ----
// 3.1.2: rendered as framework ImageVectors (see LineIconsVector.kt)
// through Material Icon — the same redesign as the bottom-bar set.
// The hand-rolled Canvas versions rendered off-center on-device in
// every release up to 3.1.1.

/** Renders one of the line-icon vectors at [size], tinted [color]. */
@Composable
private fun VectorLineIcon(vector: ImageVector, size: Dp, color: Color) {
    Icon(
        imageVector = vector,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

/** Search: circle + handle. */
@Composable
fun SearchLineIcon(size: Dp, color: Color) {
    VectorLineIcon(SearchVector, size, color)
}

/** Vertical ellipsis (one menu per row). */
@Composable
fun DotsVerticalIcon(size: Dp, color: Color) {
    VectorLineIcon(DotsVerticalVector, size, color)
}

/** Share: up arrow out of a tray. */
@Composable
fun ShareLineIcon(size: Dp, color: Color) {
    VectorLineIcon(ShareVector, size, color)
}

/** Pencil (edit). */
@Composable
fun EditLineIcon(size: Dp, color: Color) {
    VectorLineIcon(EditVector, size, color)
}

/** Two overlapping rectangles (copy / duplicate). */
@Composable
fun CopyLineIcon(size: Dp, color: Color) {
    VectorLineIcon(CopyVector, size, color)
}

/** Download: down arrow into a tray. */
@Composable
fun DownloadLineIcon(size: Dp, color: Color) {
    VectorLineIcon(DownloadVector, size, color)
}

/** Upload: up arrow out of a tray. */
@Composable
fun UploadLineIcon(size: Dp, color: Color) {
    VectorLineIcon(UploadVector, size, color)
}

/** Check mark. */
@Composable
fun CheckLineIcon(size: Dp, color: Color) {
    VectorLineIcon(CheckVector, size, color)
}

/** Down chevron (collapsible groups). */
@Composable
fun ChevronDownIcon(size: Dp, color: Color) {
    VectorLineIcon(ChevronDownVector, size, color)
}

/** Sort: three lines, long to short. */
@Composable
fun SortLineIcon(size: Dp, color: Color) {
    VectorLineIcon(SortVector, size, color)
}

/** List (logs): three lines with leading dots. */
@Composable
fun ListLineIcon(size: Dp, color: Color) {
    VectorLineIcon(ListVector, size, color)
}

/** Phone (per-app proxy): rounded handset outline. */
@Composable
fun PhoneLineIcon(size: Dp, color: Color) {
    VectorLineIcon(PhoneVector, size, color)
}

/** A simple 24dp spacer matching an icon's box (keeps rows aligned). */
@Composable
fun IconSpacer(size: Dp = 20.dp) {
    Canvas(modifier = androidx.compose.ui.Modifier.size(size)) {}
}
