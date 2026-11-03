package com.amyrmo.damavand.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amyrmo.damavand.R
import com.amyrmo.damavand.data.settings.AppLanguage
import com.amyrmo.damavand.data.settings.AppSettings
import com.amyrmo.damavand.data.settings.ConnectStyle
import com.amyrmo.damavand.logic.PerAppSelection
import com.amyrmo.damavand.logic.RoutingMode
import com.amyrmo.damavand.data.settings.ThemeMode
import com.amyrmo.damavand.data.settings.UiSettings
import com.amyrmo.damavand.platform.AppInfo
import com.amyrmo.damavand.platform.CoreVersions
import com.amyrmo.damavand.ui.AppViewModel
import com.amyrmo.damavand.ui.components.DamavandIcon
import com.amyrmo.damavand.ui.components.DamavandIconKind
import com.amyrmo.damavand.ui.components.FormTextField
import com.amyrmo.damavand.ui.components.SectionHeader
import com.amyrmo.damavand.ui.editors.PickerRow
import com.amyrmo.damavand.ui.editors.SwitchRow
import com.amyrmo.damavand.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsPage {
    MAIN, APPEARANCE, LANGUAGE, VPN, DNS, CORES, BACKUP, ABOUT
}

/**
 * Settings (§5.2): an index page (Subscriptions/Logs shortcuts + the
 * seven pages) over internal subpages — none of them are dock
 * destinations (§5.1). Every value persists through SettingsRepository:
 * UI-slice values via updateUiSettings, session values via
 * updateAppSettings.
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenSubscriptions: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val ui by vm.uiSettings.collectAsStateWithLifecycle()
    val app by vm.appSettings.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(SettingsPage.MAIN) }

    if (page != SettingsPage.MAIN) {
        Subpage(title = pageTitle(page), onBack = { page = SettingsPage.MAIN }) {
            when (page) {
                SettingsPage.APPEARANCE -> AppearancePage(vm, ui)
                SettingsPage.LANGUAGE -> LanguagePage(vm, ui)
                SettingsPage.VPN -> VpnPage(vm, app)
                SettingsPage.DNS -> DnsPage(vm, app)
                SettingsPage.CORES -> CoresPage(vm)
                SettingsPage.BACKUP -> BackupPage(vm)
                SettingsPage.ABOUT -> AboutPage()
                SettingsPage.MAIN -> Unit
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = 16.dp, bottom = Dimens.DockClearance + 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.dest_settings),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        SettingsRow(stringResource(R.string.dest_subscriptions)) { onOpenSubscriptions() }
        SettingsRow(stringResource(R.string.dest_logs)) { onOpenLogs() }
        SettingsRow(stringResource(R.string.settings_appearance)) { page = SettingsPage.APPEARANCE }
        SettingsRow(stringResource(R.string.settings_language)) { page = SettingsPage.LANGUAGE }
        SettingsRow(stringResource(R.string.settings_vpn)) { page = SettingsPage.VPN }
        SettingsRow(stringResource(R.string.settings_dns)) { page = SettingsPage.DNS }
        SettingsRow(stringResource(R.string.settings_cores)) { page = SettingsPage.CORES }
        SettingsRow(stringResource(R.string.settings_backup)) { page = SettingsPage.BACKUP }
        SettingsRow(stringResource(R.string.settings_about)) { page = SettingsPage.ABOUT }
    }
}

@Composable
private fun pageTitle(page: SettingsPage): String = stringResource(
    when (page) {
        SettingsPage.APPEARANCE -> R.string.settings_appearance
        SettingsPage.LANGUAGE -> R.string.settings_language
        SettingsPage.VPN -> R.string.settings_vpn
        SettingsPage.DNS -> R.string.settings_dns
        SettingsPage.CORES -> R.string.settings_cores
        SettingsPage.BACKUP -> R.string.settings_backup
        SettingsPage.ABOUT -> R.string.settings_about
        SettingsPage.MAIN -> R.string.dest_settings
    },
)

/** A scrollable subpage with a close-to-index header. */
@Composable
private fun Subpage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = 10.dp, bottom = Dimens.DockClearance + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                DamavandIcon(
                    kind = DamavandIconKind.Close,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}

/** An index row: label + chevron. */
@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        DamavandIcon(
            kind = DamavandIconKind.ChevronRight,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 18.dp,
        )
    }
}

@Composable
private fun AppearancePage(vm: AppViewModel, ui: UiSettings) {
    PickerRow(
        label = stringResource(R.string.appearance_theme),
        value = ui.themeMode,
        options = ThemeMode.entries,
        labelOf = {
            stringResource(
                when (it) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                },
            )
        },
        onPick = { mode -> vm.updateUiSettings { it.copy(themeMode = mode) } },
    )
    SwitchRow(
        label = stringResource(R.string.appearance_amoled),
        checked = ui.amoled,
        onCheckedChange = { value -> vm.updateUiSettings { it.copy(amoled = value) } },
    )
    SwitchRow(
        label = stringResource(R.string.appearance_reduce_transparency),
        checked = ui.reduceTransparency,
        onCheckedChange = { value -> vm.updateUiSettings { it.copy(reduceTransparency = value) } },
    )
    SwitchRow(
        label = stringResource(R.string.appearance_glass_blur),
        checked = ui.glassBlur,
        onCheckedChange = { value -> vm.updateUiSettings { it.copy(glassBlur = value) } },
    )
    PickerRow(
        label = stringResource(R.string.appearance_connect_style),
        value = ui.connectStyle,
        options = ConnectStyle.entries,
        labelOf = {
            stringResource(
                when (it) {
                    ConnectStyle.PILL -> R.string.connect_style_pill
                    ConnectStyle.SLIDE -> R.string.connect_style_slide
                },
            )
        },
        onPick = { style -> vm.updateUiSettings { it.copy(connectStyle = style) } },
    )
}

@Composable
private fun LanguagePage(vm: AppViewModel, ui: UiSettings) {
    PickerRow(
        label = stringResource(R.string.settings_language),
        value = ui.language,
        options = AppLanguage.entries,
        labelOf = {
            when (it) {
                AppLanguage.SYSTEM -> stringResource(R.string.theme_system)
                AppLanguage.FA -> "فارسی"
                AppLanguage.EN -> "English"
            }
        },
        onPick = { language -> vm.updateUiSettings { it.copy(language = language) } },
    )
    Text(
        text = stringResource(R.string.language_rtl_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private enum class PerAppMode { ALL, INCLUDE, EXCLUDE }

@Composable
private fun VpnPage(vm: AppViewModel, app: AppSettings) {
    PickerRow(
        label = stringResource(R.string.vpn_routing),
        value = app.routing,
        options = RoutingMode.entries,
        labelOf = {
            stringResource(
                when (it) {
                    RoutingMode.GLOBAL -> R.string.routing_global
                    RoutingMode.RULE_BASED -> R.string.routing_rules
                    RoutingMode.IRAN_BYPASS -> R.string.routing_iran_bypass
                },
            )
        },
        onPick = { routing -> vm.updateAppSettings { it.copy(routing = routing) } },
    )
    SwitchRow(
        label = stringResource(R.string.vpn_proxy_only),
        checked = app.proxyOnly,
        onCheckedChange = { value -> vm.updateAppSettings { it.copy(proxyOnly = value) } },
    )
    var mtuText by remember { mutableStateOf(app.device.mtu.toString()) }
    FormTextField(
        value = mtuText,
        onValueChange = { text ->
            mtuText = text.filter { it.isDigit() }
            mtuText.toIntOrNull()?.takeIf { it in 576..9000 }?.let { mtu ->
                vm.updateAppSettings { it.copy(device = it.device.copy(mtu = mtu)) }
            }
        },
        label = stringResource(R.string.vpn_mtu),
        forceLtr = true,
    )
    val perAppMode = when (app.perApp) {
        PerAppSelection.All -> PerAppMode.ALL
        is PerAppSelection.Include -> PerAppMode.INCLUDE
        is PerAppSelection.Exclude -> PerAppMode.EXCLUDE
    }
    var packagesText by remember {
        mutableStateOf(
            when (val selection = app.perApp) {
                PerAppSelection.All -> ""
                is PerAppSelection.Include -> selection.packages.joinToString("\n")
                is PerAppSelection.Exclude -> selection.packages.joinToString("\n")
            },
        )
    }
    fun applyPerApp(mode: PerAppMode, packagesRaw: String) {
        val packages = packagesRaw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        vm.updateAppSettings {
            it.copy(
                perApp = when (mode) {
                    PerAppMode.ALL -> PerAppSelection.All
                    PerAppMode.INCLUDE -> PerAppSelection.Include(packages)
                    PerAppMode.EXCLUDE -> PerAppSelection.Exclude(packages)
                },
            )
        }
    }
    PickerRow(
        label = stringResource(R.string.vpn_per_app),
        value = perAppMode,
        options = PerAppMode.entries,
        labelOf = {
            stringResource(
                when (it) {
                    PerAppMode.ALL -> R.string.per_app_all
                    PerAppMode.INCLUDE -> R.string.per_app_include
                    PerAppMode.EXCLUDE -> R.string.per_app_exclude
                },
            )
        },
        onPick = { mode -> applyPerApp(mode, packagesText) },
    )
    if (perAppMode != PerAppMode.ALL) {
        FormTextField(
            value = packagesText,
            onValueChange = { text ->
                packagesText = text
                applyPerApp(perAppMode, text)
            },
            label = stringResource(R.string.per_app_packages),
            singleLine = false,
            minLines = 3,
            forceLtr = true,
        )
    }
}

@Composable
private fun DnsPage(vm: AppViewModel, app: AppSettings) {
    var dnsText by remember { mutableStateOf(app.sessionDns) }
    FormTextField(
        value = dnsText,
        onValueChange = { text ->
            dnsText = text
            vm.updateAppSettings { it.copy(sessionDns = text.trim()) }
        },
        label = stringResource(R.string.dns_session),
        supportingText = stringResource(R.string.dns_session_hint),
        forceLtr = true,
    )
}

@Composable
private fun CoresPage(vm: AppViewModel) {
    CoreVersions.all.forEach { core ->
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = core.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = core.version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = core.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(
        text = stringResource(R.string.cores_geo_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionHeader(title = stringResource(R.string.aether_keys))
    OutlinedButton(
        onClick = { vm.clearAetherData() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.cores_aether_clear))
    }
}

@Composable
private fun BackupPage(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = vm.exportBackup()
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)
                        ?.bufferedWriter()?.use { it.write(text) }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                }.getOrNull()
            }
            if (text != null) vm.importBackup(text)
        }
    }
    Button(
        onClick = { exportLauncher.launch("damavand-backup.json") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.backup_export))
    }
    OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.backup_import))
    }
    Text(
        text = stringResource(R.string.backup_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.about_version, AppInfo.versionName(context)),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.about_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
