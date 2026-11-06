package com.amyrmo.damavand.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BentoPalette { Brand, Accent, Pastel, Vivid, Mono, Citrus, Matcha, Neon, Pistachio, Ocean, Sunset, Berry, Damavand }

@Serializable
enum class BentoCorners { Leaf, Round, Soft }

@Serializable
enum class BentoTitle { Huge, Large, Compact }

@Serializable
enum class BentoDensity { Comfortable, Compact }

@Serializable
enum class BentoDockLabels { Active, Always, Never }

@Serializable
enum class BentoConnect { Slide, Button, Morph, Rail, Hold, Tile }

@Serializable
data class BentoPrefs(
    val palette: BentoPalette = BentoPalette.Damavand,
    // Round by default (Amir's Interface defaults for Damavand 1.5, 2026-11-06).
    val corners: BentoCorners = BentoCorners.Round,
    val title: BentoTitle = BentoTitle.Large,
    val density: BentoDensity = BentoDensity.Comfortable,
    val motifs: Boolean = true,
    val playfulTouch: Boolean = true,
    val indexes: Boolean = true,
    val liveTiles: Boolean = true,
    val tiles: String = "",
    val dockLabels: BentoDockLabels = BentoDockLabels.Active,
    val dockAutoHide: Boolean = true,
    val navStyle: NavBarStyle = NavBarStyle.Dock,
    // Hold-to-connect by default (Amir's Interface defaults, 2026-11-06).
    val connect: BentoConnect = BentoConnect.Hold,
    val wave: Boolean = true,
    val homeSpeed: Boolean = true,
    val homeSession: Boolean = true,
    val homeQuick: Boolean = true,
    val homeChips: Boolean = true,
)
