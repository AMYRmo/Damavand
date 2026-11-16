package com.amyrmo.damavand.lite.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.AutoSelect
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.components.BoltVector
import com.amyrmo.damavand.lite.ui.components.CheckVector
import com.amyrmo.damavand.lite.ui.components.CircleIconButton
import com.amyrmo.damavand.lite.ui.components.InfoVector
import com.amyrmo.damavand.lite.ui.components.PlayVector
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Auto UI (3.2.8) — a Lite-styled port of Damavand's
 * ui/servers/AutoSelectUi.kt: the Auto card (a selectable server row,
 * one per group) and its details sheet (live exit, switch history,
 * per-member health, pinning, how-it-works).
 */

/** "Auto · <group>" + the live line ("<member> · <delay> ms"), or the phase / idle text. */
@Composable
fun autoSelectLiveLine(session: AutoSelect.Session, memberName: (String) -> String?): String? {
    val status = session.status ?: return null
    val name = session.selectedProfileId?.let(memberName) ?: return null
    val delay = status.member(status.selected)?.delayMs?.takeIf { it > 0 }
    return if (delay != null) stringResource(R.string.auto_live_with_delay, name, delay.toString())
    else stringResource(R.string.auto_live, name)
}

@Composable
fun AutoSelectCard(
    groupLabel: String,
    memberCount: Int,
    active: Boolean,
    live: AutoSelect.Session?,
    memberName: (String) -> String?,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = live?.status?.phase
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LiteColors.surface)
            .border(
                1.dp,
                if (active) LiteColors.accent else LiteColors.hairline,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onSelect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(phaseColor(phase)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = BoltVector,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.auto_card_title, groupLabel),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val line = live?.let { autoSelectLiveLine(it, memberName) }
                ?: phase?.let { phaseLabel(it) }
                ?: stringResource(R.string.auto_card_idle, memberCount.toString())
            Text(
                text = line,
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (active) {
            Icon(
                imageVector = CheckVector,
                contentDescription = stringResource(R.string.config_active),
                tint = LiteColors.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        CircleIconButton(
            buttonSize = 32.dp,
            glyphSize = 15.dp,
            filled = false,
            onClick = onConnect,
            glyphColor = LiteColors.accent,
            icon = PlayVector,
            contentDescription = stringResource(R.string.server_menu_connect)
        )
        Spacer(Modifier.width(8.dp))
        CircleIconButton(
            buttonSize = 32.dp,
            glyphSize = 15.dp,
            filled = false,
            onClick = onDetails,
            glyphColor = LiteColors.textSecondary,
            icon = InfoVector,
            contentDescription = stringResource(R.string.cd_details)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSelectSheet(
    groupLabel: String,
    members: List<Profile>,
    live: AutoSelect.Session?,
    onDismiss: () -> Unit,
    onPin: (String) -> Unit,
    onUnpin: () -> Unit,
) {
    val byId = members.associateBy { it.id }
    val memberName: (String) -> String? = { id -> byId[id]?.name }
    val status = live?.status
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LiteColors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(phaseColor(status?.phase)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = BoltVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.auto_card_title, groupLabel),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiteColors.text,
                        fontFamily = Vazirmatn,
                    )
                    Text(
                        status?.let { phaseLabel(it.phase) }
                            ?: stringResource(R.string.auto_not_connected),
                        fontSize = 13.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                    )
                }
            }

            if (live != null && status != null) {
                val selectedName = live.selectedProfileId?.let(memberName)
                if (selectedName != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LiteColors.surface2,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.auto_now_on),
                                fontSize = 12.sp,
                                color = LiteColors.textSecondary,
                                fontFamily = Vazirmatn,
                            )
                            Text(
                                selectedName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiteColors.text,
                                fontFamily = Vazirmatn,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            status.member(status.selected)?.let { m ->
                                Text(
                                    memberFacts(m),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = LiteColors.textSecondary,
                                )
                            }
                            if (!status.pinned.isNullOrBlank()) {
                                Spacer(Modifier.height(10.dp))
                                FilledTonalButton(onClick = onUnpin) {
                                    Text(
                                        stringResource(R.string.auto_unpin),
                                        fontFamily = Vazirmatn,
                                    )
                                }
                            }
                        }
                    }
                }

                val events = status.events.asReversed().take(6)
                if (events.isNotEmpty()) {
                    Text(
                        stringResource(R.string.auto_history),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LiteColors.accent,
                        fontFamily = Vazirmatn,
                    )
                    events.forEach { e ->
                        EventRow(e, live) { tag ->
                            live.memberProfiles[tag]?.let(memberName) ?: tag
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.auto_members),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.accent,
                fontFamily = Vazirmatn,
            )
            if (live != null && status != null) {
                val rows = live.memberProfiles.entries.sortedWith(
                    compareBy<Map.Entry<String, String>>(
                        { status.member(it.key)?.health?.ordinal?.let(::healthRank) ?: 9 },
                        { status.member(it.key)?.delayMs?.takeIf { d -> d > 0 } ?: Long.MAX_VALUE },
                    ),
                )
                rows.forEach { (tag, profileId) ->
                    LiveMemberRow(
                        name = memberName(profileId) ?: tag,
                        member = status.member(tag),
                        selected = tag == status.selected,
                        pinned = tag == status.pinned,
                        onPin = { onPin(tag) },
                    )
                }
            } else {
                members.sortedBy { (it.lastPingMs).let { p -> if (p > 0) p else Long.MAX_VALUE } }
                    .forEach { StoredMemberRow(it) }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = LiteColors.surface2,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.auto_how_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LiteColors.text,
                        fontFamily = Vazirmatn,
                    )
                    Text(
                        stringResource(R.string.auto_how_body),
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: AutoSelect.Event, live: AutoSelect.Session, nameOf: (String) -> String) {
    val to = nameOf(e.to)
    val from = e.from.takeIf { it.isNotBlank() }?.let(nameOf).orEmpty()
    val text = when (e.kind) {
        AutoSelect.Reason.Startup -> stringResource(R.string.auto_reason_startup, to)
        AutoSelect.Reason.Failover -> stringResource(R.string.auto_reason_failover, to, from)
        AutoSelect.Reason.Faster -> stringResource(R.string.auto_reason_faster, to, from)
        AutoSelect.Reason.Pinned -> stringResource(R.string.auto_reason_pinned, to)
        AutoSelect.Reason.Recovered -> stringResource(R.string.auto_reason_recovered, to)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (e.kind == AutoSelect.Reason.Failover) LiteColors.error
                    else LiteColors.accent,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = LiteColors.text,
            fontFamily = Vazirmatn,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            ago(e.atMs),
            fontSize = 11.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LiveMemberRow(
    name: String,
    member: AutoSelect.Member?,
    selected: Boolean,
    pinned: Boolean,
    onPin: () -> Unit,
) {
    val health = member?.health ?: AutoSelect.Health.Unknown
    val emphasis by animateFloatAsState(if (selected) 1f else 0.985f, label = "member-emphasis")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) LiteColors.surface2 else LiteColors.surface,
        modifier = Modifier.fillMaxWidth().scale(emphasis),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = LiteColors.text,
                        fontFamily = Vazirmatn,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        HealthChip(health)
                        if (pinned) Chip(
                            stringResource(R.string.auto_pinned),
                            LiteColors.surface2,
                        )
                        member?.let { m ->
                            Text(
                                memberFacts(m),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = LiteColors.textSecondary,
                            )
                        }
                    }
                }
                if (!pinned) {
                    TextButton(onClick = onPin) {
                        Text(
                            stringResource(R.string.auto_pin),
                            fontSize = 12.sp,
                            color = LiteColors.accent,
                            fontFamily = Vazirmatn,
                        )
                    }
                }
            }
            val error = member?.lastError?.takeIf {
                it.isNotBlank() && (health == AutoSelect.Health.Dead || health == AutoSelect.Health.Suspect)
            }
            AnimatedVisibility(visible = error != null) {
                Text(
                    error.orEmpty(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LiteColors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StoredMemberRow(p: Profile) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = LiteColors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                p.name,
                fontSize = 14.sp,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (p.lastPingMs > 0) {
                Text(
                    "${p.lastPingMs} ms",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        p.lastPingMs < 200 -> LiteColors.green
                        p.lastPingMs < 500 -> LiteColors.amber
                        else -> LiteColors.error
                    },
                    fontFamily = Vazirmatn,
                )
            }
        }
    }
}

@Composable
private fun HealthChip(h: AutoSelect.Health) {
    val (label, color) = when (h) {
        AutoSelect.Health.Alive -> stringResource(R.string.auto_health_alive) to LiteColors.green
        AutoSelect.Health.Suspect -> stringResource(R.string.auto_health_suspect) to LiteColors.amber
        AutoSelect.Health.Dead -> stringResource(R.string.auto_health_dead) to LiteColors.error
        AutoSelect.Health.Unknown -> stringResource(R.string.auto_health_unknown) to LiteColors.surface2
    }
    Chip(label, color)
}

@Composable
private fun Chip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (color == LiteColors.surface2) LiteColors.text else Color.White,
            fontFamily = Vazirmatn,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun memberFacts(m: AutoSelect.Member): String = buildString {
    if (m.delayMs > 0) {
        append("${m.delayMs} ms")
        if (m.jitterMs > 0) append(" ±${m.jitterMs}")
    }
    if (m.connections > 0) {
        if (isNotEmpty()) append(" · ")
        append(stringResource(R.string.auto_connections, m.connections.toString()))
    }
}

@Composable
private fun phaseLabel(p: AutoSelect.Phase): String = stringResource(
    when (p) {
        AutoSelect.Phase.Starting -> R.string.auto_state_starting
        AutoSelect.Phase.Ok -> R.string.auto_state_ok
        AutoSelect.Phase.Degraded -> R.string.auto_state_degraded
        AutoSelect.Phase.Down -> R.string.auto_state_down
        AutoSelect.Phase.Idle -> R.string.auto_state_idle
    },
)

private fun phaseColor(p: AutoSelect.Phase?): Color = when (p) {
    AutoSelect.Phase.Degraded -> LiteColors.amber
    AutoSelect.Phase.Down -> LiteColors.error
    AutoSelect.Phase.Ok -> LiteColors.green
    else -> LiteColors.accent
}

private fun healthRank(ordinal: Int): Int = when (AutoSelect.Health.entries[ordinal]) {
    AutoSelect.Health.Alive -> 0
    AutoSelect.Health.Suspect -> 1
    AutoSelect.Health.Unknown -> 2
    AutoSelect.Health.Dead -> 3
}

@Composable
private fun ago(atMs: Long): String {
    val seconds = ((System.currentTimeMillis() - atMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 45 -> stringResource(R.string.auto_ago_now)
        seconds < 3600 -> stringResource(R.string.auto_ago_minutes, ((seconds + 30) / 60).toString())
        else -> stringResource(R.string.auto_ago_hours, (seconds / 3600).toString())
    }
}
