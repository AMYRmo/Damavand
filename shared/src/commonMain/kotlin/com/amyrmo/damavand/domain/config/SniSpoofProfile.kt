package com.amyrmo.damavand.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class SniSpoofProfile(

    val link: String,

    val fakeSni: String,

    val cleanIp: String = "",
)
