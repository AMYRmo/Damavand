@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.amyrmo.damavand.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.data.net.PingCoordinator
import com.amyrmo.damavand.domain.config.VpnProfile
import com.amyrmo.damavand.ui.components.FlagBadge
import kotlinx.coroutines.flow.StateFlow

// Summit Servers tab (prototype): test-all button + a simple list of the
// real profiles sorted by ping, with radio, flag, core tag, a "fastest"
// badge on the best one, and ping. Tap a row to activate it.
// The full management screen stays one tap away ("Manage servers").
// NOTE: no direct Res.string.* access in this file; labels arrive as params.
@Composable
internal fun SummitServersScreen(
    profiles: List<VpnProfile>,
    activeId: String?,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    title: String,
    testAllLabel: String,
    testingLabel: String,
    fastestBadgeLabel: String,
    manageLabel: String,
    testNoteLabel: String,
    testProgress: StateFlow<PingCoordinator.Progress?>,
    onTestAll: () -> Unit,
    engineLabel: (VpnProfile) -> String,
    countryCode: (VpnProfile) -> String?,
    onActivate: (String) -> Unit,
    onManageServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by testProgress.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val sorted = profiles.sortedWith(compareBy(nullsLast()) { it.lastPingMs })
    val fastestId = sorted.firstOrNull { it.lastPingMs != null }?.id

    Box(
        modifier
            .fillMaxSize()
            .background(SummitBg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = SummitText,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )

                // ---- Test all ----
                Surface(
                    onClick = {
                        if (progress != null) PingCoordinator.cancel() else onTestAll()
                    },
                    shape = CircleShape,
                    color = SummitText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (progress != null) {
                                "$testingLabel ${progress!!.done}/${progress!!.total}"
                            } else {
                                testAllLabel
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = SummitBg,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                sorted.forEach { profile ->
                    val selected = profile.id == activeId
                    val isFastest = profile.id == fastestId
                    val code = countryCode(profile)
                    Surface(
                        onClick = { onActivate(profile.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = SummitSurface,
                        border = if (selected) {
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                SummitRed.copy(alpha = 0.6f),
                            )
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Radio (20dp).
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        if (selected) SummitRed else SummitHairlineStrong,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(SummitRed),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SummitSurface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (code != null) {
                                    FlagBadge(countryCode = code, size = 44.dp)
                                } else {
                                    Text(
                                        profile.name.firstOrNull()?.toString() ?: "؟",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = SummitTextSecondary,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = SummitText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        profile.transportLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                        ),
                                        color = SummitTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            SummitHairlineStrong,
                                        ),
                                    ) {
                                        Text(
                                            engineLabel(profile),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                            ),
                                            color = SummitTextSecondary,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    profile.lastPingMs?.let { "$it ms" } ?: "—",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = SummitText,
                                )
                                if (isFastest) {
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = SummitRed,
                                    ) {
                                        Text(
                                            fastestBadgeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = Color.White,
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 3.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    testNoteLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SummitTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    manageLabel,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = SummitTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 24.dp)
                        .clip(CircleShape)
                        .clickable { onManageServers() }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
