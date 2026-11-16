package com.amyrmo.damavand.lite.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.ConnectionUiState
import com.amyrmo.damavand.lite.ui.components.ChevronDownIcon
import com.amyrmo.damavand.lite.ui.components.FlagBadge
import com.amyrmo.damavand.lite.ui.components.RefreshVector
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * 3.2.21 (Damavand ConnectionInfoPill, main Damavand / ZedSecure):
 * the card Home shows while connected — the flag of the place the
 * tunnel exits from, the location line, the exit IP, and the ping,
 * with the details (IPv4 / ISP / location) + a refresh under a
 * chevron. Same shape as Damavand's (22dp card, 24dp flag, two
 * lines, ping on the right), in Lite's own colors.
 */
@Composable
fun ConnectionInfoPill(
    connection: ConnectionUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "info-chevron",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LiteColors.surface)
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(countryCode = connection.countryCode, size = 24.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = connection.location.ifBlank { stringResource(R.string.info_unknown_location) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = connection.ip.ifBlank { stringResource(R.string.info_no_ip) },
                    fontSize = 13.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (connection.pingMs > 0) {
                Text(
                    text = "${connection.pingMs} ms",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connection.pingMs < 250) LiteColors.green else LiteColors.error,
                    fontFamily = Vazirmatn,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { expanded = !expanded }) {
                Box(
                    Modifier.graphicsLayer { rotationZ = chevronRotation },
                    contentAlignment = Alignment.Center,
                ) {
                    ChevronDownIcon(16.dp, LiteColors.textSecondary)
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                HorizontalDivider(color = LiteColors.hairline)
                Spacer(Modifier.size(10.dp))
                InfoDetailRow(stringResource(R.string.info_ipv4), connection.ip)
                InfoDetailRow(stringResource(R.string.info_isp), connection.isp)
                InfoDetailRow(
                    stringResource(R.string.info_location),
                    connection.location.ifBlank { null },
                )
                Spacer(Modifier.size(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = RefreshVector,
                            contentDescription = stringResource(R.string.info_refresh),
                            tint = LiteColors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.info_refresh),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = LiteColors.accent,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.clickable(onClick = onRefresh),
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoDetailRow(label: String, value: String?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            modifier = Modifier.width(74.dp),
        )
        Text(
            value ?: "—",
            fontSize = 13.sp,
            color = LiteColors.text,
            fontFamily = Vazirmatn,
            maxLines = 2,
        )
    }
}
