package com.amyrmo.damavand.ui.components

import androidx.annotation.StringRes
import com.amyrmo.damavand.R

/**
 * The four shell destinations (architecture §5.1). The scaffold is a state
 * machine over this enum — no navigation graph, no routes; back from any
 * destination returns to [Home]. Editors and sheets are hosted by their
 * parent screen and are NOT destinations.
 */
enum class TopDestination(
    @StringRes val labelRes: Int,
    val icon: DamavandIconKind,
) {
    Home(R.string.dest_home, DamavandIconKind.Home),
    Servers(R.string.dest_servers, DamavandIconKind.Servers),
    Chains(R.string.dest_chains, DamavandIconKind.Chains),
    Settings(R.string.dest_settings, DamavandIconKind.Settings),
}
