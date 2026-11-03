package com.amyrmo.damavand.logic

// Ported from v1 (domain.config.AutoSelectConfig), trimmed to what the
// config generators use. The v1 `AutoMember.SingBox` variant is gone: in v2
// sing-box fragments run on the sing-box adapter (architecture §3.2), never
// inside an Xray config, so an auto-select group can only contain Xray
// servers and lifted custom-JSON outbounds.

import com.amyrmo.damavand.model.ServerConfig

object AutoSelectTags {
    const val GROUP = "proxy"

    private const val PREFIX = "proxy@"

    fun member(index: Int): String = "$PREFIX$index"

    fun isMember(tag: String): Boolean =
        tag.length > PREFIX.length && tag.startsWith(PREFIX) && tag.substring(PREFIX.length).all { it.isDigit() }
}

sealed interface AutoMember {
    val profileId: String

    data class Server(override val profileId: String, val server: ServerConfig) : AutoMember

    data class Custom(override val profileId: String, val rawJson: String) : AutoMember
}

data class AutoSelectTuning(
    val probeUrl: String = "",
    val probeTimeoutSec: Int = 5,

    val activeIntervalSec: Int = 30,

    val sweepIntervalMin: Int = 10,

    val switchMarginPercent: Int = 30,

    val retry: Boolean = true,

    val initialProfileId: String? = null,
)

data class RuleGroup(
    val members: List<AutoMember>,

    val initialProfileId: String? = null,
)

data class AutoSelectBuild(
    val json: String,

    val memberProfiles: Map<String, String>,

    val skipped: List<String>,
)

class AutoSelectNoMembersException(val skipped: Int) :
    Exception("auto-select has no usable server ($skipped skipped)")
