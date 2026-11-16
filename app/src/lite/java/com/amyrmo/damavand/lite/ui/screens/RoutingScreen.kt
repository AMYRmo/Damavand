package com.amyrmo.damavand.lite.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.RoutingRule
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.PlusLineIcon
import com.amyrmo.damavand.lite.ui.components.TrashLineIcon
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * Routing (3.1.0, PattNG): the mode (all through the tunnel /
 * bypass LAN / custom rules) plus the custom rule list. Rules apply
 * from the next connection.
 */
@Composable
fun RoutingScreen(vm: LiteViewModel, onBack: () -> Unit) {
    val mode by vm.routingMode.collectAsState(initial = "bypass_lan")
    val rules by vm.routingRules.collectAsState()
    var showAddRule by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<RoutingRule?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        SubScreenHeader(titleRes = R.string.routing_title, onBack = onBack)
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ModeRow(
                    titleRes = R.string.routing_mode_global,
                    descRes = R.string.routing_mode_global_desc,
                    selected = mode == "global",
                    onClick = { vm.setRoutingMode("global") }
                )
                Spacer(Modifier.height(12.dp))
                ModeRow(
                    titleRes = R.string.routing_mode_bypass_lan,
                    descRes = R.string.routing_mode_bypass_lan_desc,
                    selected = mode == "bypass_lan",
                    onClick = { vm.setRoutingMode("bypass_lan") }
                )
                Spacer(Modifier.height(12.dp))
                ModeRow(
                    titleRes = R.string.routing_mode_custom,
                    descRes = R.string.routing_mode_custom_desc,
                    selected = mode == "custom",
                    onClick = { vm.setRoutingMode("custom") }
                )
                Spacer(Modifier.height(24.dp))
            }
            if (mode == "custom") {
                item {
                    val addRuleLabel = stringResource(R.string.routing_add_rule)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.routing_rules_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(LiteColors.accent, CircleShape)
                                .clickable(role = Role.Button, onClick = { showAddRule = true })
                                .semantics { contentDescription = addRuleLabel },
                            contentAlignment = Alignment.Center
                        ) {
                            PlusLineIcon(size = 18.dp, color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Preset rulesets (3.2.0, PattNG): one tap imports
                    // the bypass-Iran or block-ads rules.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetChip(
                            labelRes = R.string.routing_preset_bypass_ir,
                            onClick = { vm.importRoutingPreset("bypass_ir") }
                        )
                        PresetChip(
                            labelRes = R.string.routing_preset_block_ads,
                            onClick = { vm.importRoutingPreset("block_ads") }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Built-in rules (3.2.0): the config always applies
                    // these — shown locked, they cannot be edited here.
                    Text(
                        text = stringResource(R.string.routing_builtin_title),
                        fontSize = 12.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn
                    )
                    Spacer(Modifier.height(8.dp))
                    BuiltinRuleRow(
                        label = stringResource(R.string.routing_builtin_private),
                        action = stringResource(R.string.routing_action_direct)
                    )
                    Spacer(Modifier.height(8.dp))
                    BuiltinRuleRow(
                        label = stringResource(R.string.routing_builtin_torrent),
                        action = stringResource(R.string.routing_action_block)
                    )
                    Spacer(Modifier.height(8.dp))
                    BuiltinRuleRow(
                        label = stringResource(R.string.routing_builtin_ads),
                        action = stringResource(R.string.routing_action_block)
                    )
                    Spacer(Modifier.height(16.dp))
                    if (rules.isEmpty()) {
                        Text(
                            text = stringResource(R.string.routing_empty),
                            fontSize = 13.sp,
                            color = LiteColors.textMuted,
                            fontFamily = Vazirmatn
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    RoutingRuleRow(
                        rule = rule,
                        onToggle = { vm.toggleRoutingRule(rule) },
                        onDelete = { ruleToDelete = rule }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddRule) {
        AddRuleDialog(
            onDismiss = { showAddRule = false },
            onAdd = { name, pattern, action ->
                vm.addRoutingRule(name, pattern, action)
                showAddRule = false
            }
        )
    }
    val deleting = ruleToDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.routing_delete_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.routing_delete_body, deleting.name),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteRoutingRule(deleting.id)
                        ruleToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.server_delete_confirm),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiteColors.error,
                        fontFamily = Vazirmatn
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text(
                        text = stringResource(R.string.sub_import_cancel),
                        fontSize = 14.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn
                    )
                }
            },
            containerColor = LiteColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ModeRow(
    titleRes: Int,
    descRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (selected) LiteColors.hairlineStrong else LiteColors.hairline,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(descRes),
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn
            )
        }
    }
}

@Composable
private fun PresetChip(labelRes: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .background(Color.Transparent, CircleShape)
            .border(1.5.dp, LiteColors.hairlineStrong, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = LiteColors.text,
            fontFamily = Vazirmatn
        )
    }
}

/** A built-in rule row, locked (3.2.0): shown, never editable. */
@Composable
private fun BuiltinRuleRow(label: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiteColors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = action,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LiteColors.textMuted,
            fontFamily = Vazirmatn
        )
    }
}

@Composable
private fun RoutingRuleRow(
    rule: RoutingRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(LiteColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = rule.pattern,
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val actionRes = when (rule.action) {
                "direct" -> R.string.routing_action_direct
                "block" -> R.string.routing_action_block
                else -> R.string.routing_action_proxy
            }
            Text(
                text = stringResource(actionRes),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when (rule.action) {
                    "block" -> LiteColors.error
                    "direct" -> LiteColors.textSecondary
                    else -> LiteColors.accent
                },
                fontFamily = Vazirmatn
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LiteColors.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = LiteColors.text,
                uncheckedTrackColor = LiteColors.surface2,
                uncheckedBorderColor = LiteColors.hairlineStrong
            )
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.Transparent, CircleShape)
                .border(1.dp, LiteColors.hairline, CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            TrashLineIcon(size = 16.dp, color = LiteColors.error)
        }
    }
}

@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("proxy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.routing_add_rule),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.routing_rule_name),
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.routing_rule_pattern),
                            fontSize = 13.sp,
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                for ((value, res) in listOf(
                    "proxy" to R.string.routing_action_proxy,
                    "direct" to R.string.routing_action_direct,
                    "block" to R.string.routing_action_block,
                )) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .clickable { action = value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(1.5.dp, LiteColors.hairlineStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (action == value) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(LiteColors.accent, CircleShape)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(res),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, pattern, action) },
                enabled = pattern.isNotBlank()
            ) {
                Text(
                    text = stringResource(R.string.routing_add_rule),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sub_import_cancel),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        containerColor = LiteColors.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
