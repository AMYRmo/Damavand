package com.amyrmo.damavand.ui.settings

import com.amyrmo.damavand.domain.model.ConnectButtonStyle
import com.amyrmo.damavand.shared.resources.Res
import com.amyrmo.damavand.shared.resources.connect_style_bar
import com.amyrmo.damavand.shared.resources.connect_style_hero
import com.amyrmo.damavand.shared.resources.connect_style_icon
import com.amyrmo.damavand.shared.resources.connect_style_pill
import com.amyrmo.damavand.shared.resources.connect_style_ring
import com.amyrmo.damavand.shared.resources.connect_style_switch
import org.jetbrains.compose.resources.StringResource

fun ConnectButtonStyle.labelRes(): StringResource = when (this) {
    ConnectButtonStyle.Pill -> Res.string.connect_style_pill
    ConnectButtonStyle.Hero -> Res.string.connect_style_hero
    ConnectButtonStyle.Ring -> Res.string.connect_style_ring
    ConnectButtonStyle.Bar -> Res.string.connect_style_bar
    ConnectButtonStyle.Icon -> Res.string.connect_style_icon
    ConnectButtonStyle.Switch -> Res.string.connect_style_switch
}
