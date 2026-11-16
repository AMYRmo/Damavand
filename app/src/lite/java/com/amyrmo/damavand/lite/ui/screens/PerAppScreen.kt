package com.amyrmo.damavand.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-app proxy (3.1.0, PattNG): choose which apps bypass the VPN
 * or are the only ones that use it. Applies from the next connection.
 */
@Composable
fun PerAppScreen(vm: LiteViewModel, onBack: () -> Unit) {
    val mode by vm.perAppMode.collectAsState(initial = "bypass")
    val selected by vm.perAppPackages.collectAsState()
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { vm.installedApps() }
    }

    val filtered = remember(apps, query) {
        val q = query.trim()
        val list = apps.orEmpty()
        if (q.isEmpty()) list else list.filter {
            it.first.contains(q, ignoreCase = true) || it.second.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        SubScreenHeader(titleRes = R.string.perapp_title, onBack = onBack)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .background(LiteColors.surface, RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    if (mode == "bypass") LiteColors.hairlineStrong else LiteColors.hairline,
                    RoundedCornerShape(20.dp)
                )
                .clickable { vm.setPerAppMode("bypass") }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeRadio(selected = mode == "bypass")
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.perapp_mode_bypass),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.perapp_mode_bypass_desc),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .background(LiteColors.surface, RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    if (mode == "allow_only") LiteColors.hairlineStrong else LiteColors.hairline,
                    RoundedCornerShape(20.dp)
                )
                .clickable { vm.setPerAppMode("allow_only") }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeRadio(selected = mode == "allow_only")
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.perapp_mode_allow),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.perapp_mode_allow_desc),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.perapp_search_hint),
                    fontSize = 14.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn
                )
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Vazirmatn, fontSize = 14.sp, color = LiteColors.text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiteColors.hairlineStrong,
                unfocusedBorderColor = LiteColors.hairline,
                cursorColor = LiteColors.accent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.perapp_applies_next),
            fontSize = 12.sp,
            color = LiteColors.textMuted,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(12.dp))
        when {
            apps == null -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = LiteColors.accent
                    )
                }
            }
            filtered.isEmpty() -> {
                Text(
                    text = stringResource(R.string.perapp_empty),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.second }) { (label, pkg) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .background(LiteColors.surface, RoundedCornerShape(20.dp))
                                .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LiteColors.text,
                                    fontFamily = Vazirmatn,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = pkg,
                                    fontSize = 11.sp,
                                    color = LiteColors.textMuted,
                                    fontFamily = Vazirmatn,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = pkg in selected,
                                onCheckedChange = { vm.togglePerAppPackage(pkg) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = LiteColors.accent,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedThumbColor = LiteColors.text,
                                    uncheckedTrackColor = LiteColors.surface2,
                                    uncheckedBorderColor = LiteColors.hairlineStrong
                                )
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ModeRadio(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(1.5.dp, LiteColors.hairlineStrong, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(LiteColors.accent, CircleShape)
            )
        }
    }
}
