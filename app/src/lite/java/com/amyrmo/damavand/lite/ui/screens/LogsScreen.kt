package com.amyrmo.damavand.lite.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.ShareLineIcon
import com.amyrmo.damavand.lite.ui.components.TrashLineIcon
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Log viewer (3.1.0, PattNG): the app's event log, live, with a
 * text filter, clear and share. Newest lines at the bottom.
 */
@Composable
fun LogsScreen(vm: LiteViewModel, onBack: () -> Unit) {
    val lines by vm.logLines.collectAsState()
    val coreLines by vm.coreLogLines.collectAsState()
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    var showCore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Load the core log when the core tab is opened.
    LaunchedEffect(showCore) {
        if (showCore) vm.refreshCoreLog()
    }

    val sourceLines = if (showCore) coreLines else lines
    val shown = remember(sourceLines, filter) {
        val q = filter.trim()
        if (q.isEmpty()) sourceLines else sourceLines.filter { it.contains(q, ignoreCase = true) }
    }
    // Follow the tail only while the user is at the bottom, so reading
    // older lines isn't interrupted.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }
    // Keyed on the newest line, not the size: once the 300-line buffer
    // is full the size stops changing and the list stopped following.
    LaunchedEffect(shown.size, shown.lastOrNull()) {
        if (shown.isNotEmpty() && atBottom) listState.scrollToItem(shown.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                SubScreenHeader(titleRes = R.string.logs_title, onBack = onBack)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Transparent, CircleShape)
                    .border(1.dp, LiteColors.hairline, CircleShape)
                    .clickable(onClick = {
                        if (showCore) vm.clearCoreLog() else vm.clearLogs()
                    }),
                contentAlignment = Alignment.Center
            ) {
                TrashLineIcon(size = 18.dp, color = LiteColors.error)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Transparent, CircleShape)
                    .border(1.dp, LiteColors.hairline, CircleShape)
                    .clickable(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shown.joinToString("\n"))
                            }
                            context.startActivity(Intent.createChooser(intent, "Damavand Lite"))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                ShareLineIcon(size = 18.dp, color = LiteColors.text)
            }
        }
        Spacer(Modifier.height(16.dp))
        // Source switch (3.2.0): app log vs the Xray core error log.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogSourceChip(
                labelRes = R.string.logs_source_app,
                selected = !showCore,
                onClick = { showCore = false }
            )
            LogSourceChip(
                labelRes = R.string.logs_source_core,
                selected = showCore,
                onClick = { showCore = true }
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.servers_search_hint),
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
        Spacer(Modifier.height(12.dp))
        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.logs_empty),
                fontSize = 14.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(LiteColors.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                items(shown) { line ->
                    Text(
                        text = line,
                        fontSize = 11.5.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        style = TextStyle(textDirection = TextDirection.Ltr),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** A pill for the log-source switch (3.2.0). */
@Composable
private fun LogSourceChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .background(
                if (selected) LiteColors.accent else Color.Transparent,
                CircleShape
            )
            .border(1.5.dp, if (selected) LiteColors.accent else LiteColors.hairlineStrong, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else LiteColors.text,
            fontFamily = Vazirmatn
        )
    }
}
