package com.amyrmo.damavand.lite.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.aether.AetherArgs
import com.amyrmo.damavand.lite.aether.AetherCore
import com.amyrmo.damavand.lite.aether.AetherEngine
import com.amyrmo.damavand.lite.aether.AetherFingerprint
import com.amyrmo.damavand.lite.aether.AetherKeyKind
import com.amyrmo.damavand.lite.aether.AetherKeys
import com.amyrmo.damavand.lite.aether.AetherProtocol
import com.amyrmo.damavand.lite.aether.AetherPsiphon
import com.amyrmo.damavand.lite.aether.AetherPsiphonCdnSet
import com.amyrmo.damavand.lite.aether.AetherPsiphonMode
import com.amyrmo.damavand.lite.aether.AetherSettings
import com.amyrmo.damavand.lite.aether.AetherTor
import com.amyrmo.damavand.lite.aether.AetherTorBridges
import com.amyrmo.damavand.lite.aether.AetherTorRelays
import com.amyrmo.damavand.lite.aether.AetherTransport
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.ChevronDownIcon
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn

/**
 * The add and edit dialogs of an Aether profile, and the page for getting new WARP keys.
 *
 * 3.2.19: both screens are a faithful port of PattNG's ServerAetherActivity and
 * ServerAetherKeysActivity — the same fields, the same conditions for when a field shows, the same
 * collapsible sections ("CDN edge lists to scan", "Other settings"), the same keys page with its radio
 * kinds, its editable dropdowns, its log panel and its key lines. What Lite has no support for is left
 * out openly: the endpoint Scan button and the editor's log panel (no scan support in the view model),
 * the "Clear Psiphon data" button (no view-model support), the exit-country dropdown (a plain text
 * field instead — the region list was never ported), and CommonTargetStrategyField (no Lite model).
 * Where PattNG's code and Amir's screenshots disagree — the dialMode field, a plain text field in
 * PattNG's code but a chevron dropdown in the screenshots — the screenshots win: an editable
 * dropdown whose only preset is "default", free typing kept. The colours are Lite's own, not PattNG's.
 */

/**
 * The names of the API servers, as PattNG's aether_api_name_options: the two Cloudflare hosts, in
 * that order.
 */
private val AETHER_API_NAME_OPTIONS = listOf("api.devices.cloudflare.com", "api.cloudflareclient.com")

/** The DNS resolvers PattNG offers for the ECH handshake, as its aether_ech_dns_options. */
private val AETHER_ECH_DNS_OPTIONS = listOf(
    "udp://1.1.1.1",
    "udp://8.8.8.8",
    "https://1.1.1.1/dns-query@sni=www.microsoft.com",
    "https://doq.dns4all.eu/dns-query@address=194.0.5.3",
    "tcp://1.1.1.1",
    "tcp://8.8.8.8",
)

/** The ECH domains PattNG offers, as its aether_ech_domain_options. */
private val AETHER_ECH_DOMAIN_OPTIONS = listOf(
    "cloudflare-ech.com",
    "crypto.cloudflare.com",
    "ip.gs",
    "api.cloudflareclient.com",
    "consumer-masque.cloudflareclient.com",
    "consumer-masque-proxy.cloudflareclient.com",
)

/** The labels of the CDN edge lists, in the order of [AetherPsiphonCdnSet.entries]. */
private val AETHER_CDN_SET_LABELS = listOf(
    "Cloudflare",
    "Fastly",
    "CloudFront",
    "Akamai (Psiphon list)",
    "Bunny (Psiphon list)",
    "Vercel",
    "GitHub",
    "Curated fronting edges",
    "Legacy Android overrides",
)

/** The label of a WARP key kind, as PattNG shows it on its keys page. */
private fun aetherKeyKindLabel(kind: AetherKeyKind): String = when (kind) {
    AetherKeyKind.ALL -> "All"
    AetherKeyKind.WIREGUARD -> "WireGuard"
    AetherKeyKind.MASQUE -> "MASQUE"
    AetherKeyKind.GOOL -> "WARP-in-WARP"
    AetherKeyKind.MIM -> "MASQUE-in-MASQUE"
    AetherKeyKind.WG_OVER_MASQUE -> "WireGuard over MASQUE"
}

@Composable
fun AetherAddDialog(vm: LiteViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    AetherConfigEditor(
        vm = vm,
        initialName = "",
        initial = AetherSettings(),
        profiles = profiles,
        titleRes = R.string.aether_add_title,
        confirmRes = R.string.manual_add,
        onSave = { name, settings ->
            vm.addAetherProfile(name, settings) { profile ->
                if (profile != null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.manual_added),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }
        },
        onDismiss = onDismiss
    )
}

@Composable
fun AetherEditDialog(profile: Profile, vm: LiteViewModel, onDismiss: () -> Unit) {
    val initial = remember(profile.aetherJson) { AetherSettings.fromJson(profile.aetherJson) }
    val profiles by vm.profiles.collectAsState()
    AetherConfigEditor(
        vm = vm,
        initialName = profile.name,
        initial = initial,
        profiles = profiles,
        titleRes = R.string.aether_edit_title,
        confirmRes = R.string.server_edit_save,
        onSave = { name, settings ->
            vm.updateAetherProfile(profile.id, name, settings)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

@Composable
private fun AetherConfigEditor(
    vm: LiteViewModel,
    initialName: String,
    initial: AetherSettings,
    profiles: List<Profile>,
    titleRes: Int,
    confirmRes: Int,
    onSave: (String, AetherSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var protocol by remember { mutableStateOf(initial.protocol.orEmpty()) }
    var transport by remember { mutableStateOf(initial.transport.orEmpty()) }
    var masqueSni by remember { mutableStateOf(initial.masqueSni.orEmpty()) }
    var scanMode by remember { mutableStateOf(initial.scanMode.orEmpty()) }
    var obfuscation by remember { mutableStateOf(initial.obfuscation.orEmpty()) }
    var fingerprint by remember { mutableStateOf(initial.fingerprint.orEmpty()) }
    var ipVersion by remember { mutableStateOf(initial.ipVersion.orEmpty()) }
    var wiwOuter by remember { mutableStateOf(initial.wiwOuter.orEmpty()) }
    var wiwInner by remember { mutableStateOf(initial.wiwInner.orEmpty()) }
    var fragment by remember { mutableStateOf(initial.fragment ?: false) }
    var fragmentSize by remember { mutableStateOf(initial.fragmentSize.orEmpty()) }
    var fragmentDelay by remember { mutableStateOf(initial.fragmentDelay.orEmpty()) }
    var ech by remember { mutableStateOf(initial.ech ?: false) }
    var echDns by remember { mutableStateOf(initial.echDns.orEmpty()) }
    var echDomain by remember { mutableStateOf(initial.echDomain.orEmpty()) }
    var dns by remember { mutableStateOf(initial.dns.orEmpty()) }
    var exitLoc by remember { mutableStateOf(initial.exitLoc.orEmpty()) }
    var exitNode by remember { mutableStateOf(initial.exitNode.orEmpty()) }
    var finalMask by remember { mutableStateOf(initial.finalMask.orEmpty()) }
    var dialMode by remember { mutableStateOf(initial.dialMode.orEmpty()) }
    var psiphon by remember { mutableStateOf(initial.psiphon.orEmpty()) }
    var psiphonMode by remember { mutableStateOf(initial.psiphonMode.orEmpty()) }
    var psiphonCdnIps by remember { mutableStateOf(initial.psiphonCdnIps.orEmpty()) }
    var psiphonCdnSni by remember { mutableStateOf(initial.psiphonCdnSni.orEmpty()) }
    var cdnSets by remember { mutableStateOf(AetherPsiphonCdnSet.parse(initial.psiphonCdnSets)) }
    var psiphonRegion by remember { mutableStateOf(initial.psiphonRegion.orEmpty().trim().uppercase(Locale.ROOT)) }
    var psiphonBundledList by remember { mutableStateOf(initial.psiphonBundledList ?: true) }
    var tor by remember { mutableStateOf(initial.tor.orEmpty()) }
    var torBridges by remember { mutableStateOf(initial.torBridges.orEmpty()) }
    var torRelays by remember { mutableStateOf(initial.torRelays.orEmpty()) }
    var torBridgeLines by remember { mutableStateOf(initial.torBridgeLines.orEmpty()) }
    var command by remember { mutableStateOf(initial.command.orEmpty()) }
    var server by remember { mutableStateOf(initial.server.orEmpty()) }
    var serverPort by remember { mutableStateOf(initial.serverPort.orEmpty()) }
    // 3.2.11 (PattNG ServerAetherKeysActivity): the WARP keys page opens
    // from the Aether editor, as in PattNG — a dialog, not a new screen,
    // so the Summit UI stays.
    var showKeys by remember { mutableStateOf(false) }
    var showOther by rememberSaveable { mutableStateOf(dns.isNotBlank() || exitLoc.isNotBlank()) }
    var showCdnSets by rememberSaveable { mutableStateOf(cdnSets.isNotEmpty()) }

    val protocolEnum = AetherProtocol.fromString(protocol)
    val psiphonEnum = AetherPsiphon.fromString(psiphon)
    val psiphonModeEnum = AetherPsiphonMode.fromString(psiphonMode)
    val torEnum = AetherTor.fromString(tor)
    val torBridgesEnum = AetherTorBridges.fromString(torBridges)
    val warpUsed = psiphonEnum != AetherPsiphon.ONLY && torEnum != AetherTor.ONLY
    val transportEnum = AetherTransport.fromString(transport)
    val usesHttp2 = protocolEnum.overMasque && transportEnum == AetherTransport.HTTP2
    val overHttp2 = AetherArgs.masqueOverHttp2(protocolEnum, transportEnum, torEnum, psiphonEnum)
    val twoHops = protocolEnum == AetherProtocol.GOOL || protocolEnum == AetherProtocol.MIM ||
        protocolEnum == AetherProtocol.WG_OVER_MASQUE
    val freedom = exitNode.isBlank()
    val isPsiphonAvailable = remember { AetherEngine.isPsiphonSupported(context) }
    val isTorTransportsAvailable = remember { AetherEngine.isTorTransportsSupported(context) }
    val exitNames = profiles.filter { !it.isAether }.map { it.name }

    // 3.2.25 (PattNG AetherRegionField): the Exit country list —
    // regions the bundled Psiphon list offers plus what the core
    // last reported, each shown as its localized name with its code,
    // sorted by name; the profile's own region stays an option even
    // before the list loads, and "Any country" means no region.
    val psiphonRegions by vm.psiphonRegions.collectAsState()
    LaunchedEffect(Unit) { vm.loadPsiphonRegions() }
    val regionLocale = LocalConfiguration.current.locales[0]
    fun regionName(code: String): String =
        runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(regionLocale) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
    val regionCurrent = psiphonRegion.trim().uppercase(Locale.ROOT)
    val regionCodes = (psiphonRegions + listOfNotNull(regionCurrent.takeIf { it.isNotEmpty() })).distinct().sortedBy { regionName(it) }
    val regionItems = listOf(stringResource(R.string.aether_psiphon_region_any) to "") +
        regionCodes.map { code -> "${regionName(code)} ($code)" to code }

    /** The settings as they stand, for the command line below and for saving. */
    val edited = initial.copy(
        protocol = protocol.ifEmpty { null },
        transport = transport.ifEmpty { null },
        masqueSni = masqueSni.ifEmpty { null },
        scanMode = scanMode.ifEmpty { null },
        obfuscation = obfuscation.ifEmpty { null },
        fingerprint = fingerprint.ifEmpty { null },
        ipVersion = ipVersion.ifEmpty { null },
        wiwOuter = wiwOuter.ifEmpty { null },
        wiwInner = wiwInner.ifEmpty { null },
        fragment = fragment,
        fragmentSize = fragmentSize.ifEmpty { null },
        fragmentDelay = fragmentDelay.ifEmpty { null },
        ech = ech,
        echDns = echDns.ifEmpty { null },
        echDomain = echDomain.ifEmpty { null },
        dns = dns.ifEmpty { null },
        exitLoc = exitLoc.ifEmpty { null },
        exitNode = exitNode.ifEmpty { null },
        finalMask = finalMask.ifEmpty { null },
        dialMode = dialMode.ifEmpty { null },
        psiphon = psiphon.ifEmpty { null },
        psiphonMode = psiphonMode.ifEmpty { null },
        psiphonCdnIps = psiphonCdnIps.ifEmpty { null },
        psiphonCdnSni = psiphonCdnSni.ifEmpty { null },
        psiphonCdnSets = AetherPsiphonCdnSet.join(cdnSets),
        psiphonRegion = psiphonRegion.ifEmpty { null },
        psiphonBundledList = psiphonBundledList,
        tor = tor.ifEmpty { null },
        torBridges = torBridges.ifEmpty { null },
        torBridgeLines = torBridgeLines.ifEmpty { null },
        torRelays = torRelays.ifEmpty { null },
        command = command.ifEmpty { null },
        server = server.ifEmpty { null },
        serverPort = serverPort.ifEmpty { null },
    )
    val builtCommand = runCatching { AetherCore.of(edited).command }.getOrDefault("")
    val customCommand = command.isNotBlank() && command.trim() != builtCommand

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(titleRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // The WARP keys page, at the top of the form, as in PattNG.
                OutlinedButton(
                    onClick = { showKeys = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(stringResource(R.string.aether_keys_get_keys), fontFamily = Vazirmatn)
                }

                AetherField(
                    labelRes = R.string.manual_name,
                    value = name,
                    onValueChange = { name = it }
                )

                if (warpUsed) {
                    AetherDropdownField(
                        labelRes = R.string.aether_protocol,
                        value = protocol,
                        items = listOf(
                            "MASQUE" to "masque",
                            "WireGuard" to "wg",
                            "WARP-in-WARP" to "gool",
                            "MASQUE-in-MASQUE" to "mim",
                            "WireGuard over MASQUE" to "wg-over-masque",
                        ),
                        defaultValue = "wg",
                        onSelect = { protocol = it },
                    )
                    if (protocolEnum.overMasque) {
                        AetherDropdownField(
                            labelRes = R.string.aether_transport,
                            value = transport,
                            items = listOf("HTTP/3" to "h3", "HTTP/2" to "h2"),
                            defaultValue = "h3",
                            onSelect = { transport = it },
                        )
                        AetherField(
                            labelRes = R.string.aether_masque_sni,
                            value = masqueSni,
                            onValueChange = { masqueSni = it },
                        )
                        if (usesHttp2) {
                            AetherSwitchRow(
                                labelRes = R.string.aether_fragment,
                                checked = fragment,
                                onChange = { fragment = it },
                            )
                            if (fragment) {
                                AetherField(
                                    labelRes = R.string.aether_keys_fragment_size,
                                    value = fragmentSize,
                                    onValueChange = { fragmentSize = it },
                                    placeholder = stringResource(R.string.aether_hint_fragment_size),
                                )
                                AetherField(
                                    labelRes = R.string.aether_keys_fragment_delay,
                                    value = fragmentDelay,
                                    onValueChange = { fragmentDelay = it },
                                    placeholder = stringResource(R.string.aether_hint_fragment_delay),
                                )
                            }
                        }
                        AetherSwitchRow(
                            labelRes = R.string.aether_ech,
                            summaryRes = R.string.aether_hint_ech,
                            checked = ech,
                            onChange = { ech = it },
                        )
                        if (ech) {
                            AetherDropdownField(
                                labelRes = R.string.aether_keys_ech_dns,
                                value = echDns,
                                items = AETHER_ECH_DNS_OPTIONS.map { it to it },
                                editable = true,
                                onSelect = { echDns = it },
                            )
                            AetherDropdownField(
                                labelRes = R.string.aether_keys_ech_domain,
                                value = echDomain,
                                items = AETHER_ECH_DOMAIN_OPTIONS.map { it to it },
                                editable = true,
                                onSelect = { echDomain = it },
                            )
                        }
                    }
                    AetherDropdownField(
                        labelRes = R.string.aether_scan,
                        value = scanMode,
                        items = listOf(
                            "Turbo" to "turbo",
                            "Balanced" to "balanced",
                            "Thorough" to "thorough",
                            "Verified" to "verified",
                            "Ironclad" to "ironclad",
                        ),
                        defaultValue = "balanced",
                        onSelect = { scanMode = it },
                    )
                    if (!overHttp2) {
                        AetherDropdownField(
                            labelRes = R.string.aether_obfuscation,
                            value = obfuscation,
                            items = listOf(
                                "Automatic" to "auto",
                                "Off" to "off",
                                "Light" to "light",
                                "Firewall" to "firewall",
                                "Balanced" to "balanced",
                                "GFW" to "gfw",
                                "Aggressive" to "aggressive",
                            ),
                            defaultValue = "auto",
                            onSelect = { obfuscation = it },
                        )
                    }
                    if (protocolEnum.overMasque) {
                        AetherDropdownField(
                            labelRes = R.string.aether_fingerprint,
                            value = fingerprint,
                            items = listOf(
                                "Chrome" to "chrome",
                                "Chrome 120" to "chrome-120",
                                "Firefox" to "firefox",
                                "Semi-Python" to "semi-python",
                                "Go" to "go",
                            ),
                            defaultValue = "chrome",
                            onSelect = { fingerprint = it },
                        )
                    }
                }

                AetherPickerRow(
                    labelRes = R.string.aether_exit_node,
                    options = exitNames,
                    selected = exitNode,
                    noneLabel = stringResource(R.string.aether_exit_node_freedom),
                    supportingText = if (exitNode.isNotBlank()) stringResource(R.string.aether_hint_exit_node) else null,
                    onSelect = { exitNode = it },
                )
                AetherPresetField(
                    labelRes = R.string.aether_keys_final_mask,
                    value = finalMask,
                    onValueChange = { finalMask = it },
                    enabled = freedom,
                )
                AetherDropdownField(
                    labelRes = R.string.aether_keys_dial_mode,
                    value = dialMode,
                    items = emptyList(),
                    editable = true,
                    defaultOption = stringResource(R.string.preset_default),
                    supportingText = if (dialMode.isBlank()) stringResource(R.string.preset_default) else null,
                    enabled = freedom,
                    onSelect = { dialMode = it },
                )

                AetherDropdownField(
                    labelRes = R.string.aether_psiphon,
                    value = psiphon,
                    items = listOf(
                        "Off" to "off",
                        "WARP → Psiphon" to "chain",
                        "Psiphon → WARP" to "reverse",
                        "Psiphon only" to "only",
                    ),
                    defaultValue = "off",
                    onSelect = { psiphon = it },
                )
                if (psiphonEnum != AetherPsiphon.OFF) {
                    if (!isPsiphonAvailable) {
                        Text(
                            text = stringResource(R.string.conn_aether_no_psiphon),
                            fontSize = 13.sp,
                            color = LiteColors.error,
                            fontFamily = Vazirmatn,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.aether_psiphon_hint),
                        fontSize = 13.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    AetherDropdownField(
                        labelRes = R.string.aether_psiphon_mode,
                        value = psiphonMode,
                        items = listOf(
                            "Automatic" to "auto",
                            "CDN fronting only" to "cdn",
                            "Direct only" to "direct",
                        ),
                        defaultValue = "auto",
                        onSelect = { psiphonMode = it },
                    )
                    if (psiphonModeEnum != AetherPsiphonMode.DIRECT) {
                        AetherField(
                            labelRes = R.string.aether_psiphon_cdn_ips,
                            value = psiphonCdnIps,
                            onValueChange = { psiphonCdnIps = it },
                            placeholder = stringResource(R.string.aether_hint_psiphon_list),
                        )
                        if (psiphonCdnIps.isNotBlank()) {
                            AetherField(
                                labelRes = R.string.aether_psiphon_cdn_sni,
                                value = psiphonCdnSni,
                                onValueChange = { psiphonCdnSni = it },
                                placeholder = stringResource(R.string.aether_hint_psiphon_list),
                            )
                        }
                        AetherSection(
                            titleRes = R.string.aether_psiphon_cdn_sets,
                            expanded = showCdnSets,
                            onExpandedChange = { showCdnSets = it },
                            summary = if (cdnSets.isEmpty()) {
                                stringResource(R.string.aether_psiphon_cdn_sets_all)
                            } else {
                                cdnSets.joinToString(", ") { AETHER_CDN_SET_LABELS[it.ordinal] }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.aether_hint_psiphon_cdn_sets),
                                fontSize = 13.sp,
                                color = LiteColors.textSecondary,
                                fontFamily = Vazirmatn,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            )
                            for (set in AetherPsiphonCdnSet.entries) {
                                AetherCheckRow(
                                    label = AETHER_CDN_SET_LABELS[set.ordinal],
                                    checked = set in cdnSets,
                                    onCheckedChange = { checked ->
                                        cdnSets = if (checked) cdnSets + set else cdnSets - set
                                    },
                                )
                            }
                        }
                        AetherDropdownField(
                            labelRes = R.string.aether_psiphon_region,
                            value = psiphonRegion,
                            items = regionItems,
                            onSelect = { psiphonRegion = it },
                        )
                        AetherSwitchRow(
                            labelRes = R.string.aether_psiphon_bundled_list,
                            summaryRes = R.string.aether_hint_psiphon_bundled_list,
                            checked = psiphonBundledList,
                            onChange = { psiphonBundledList = it },
                        )
                    }
                }

                AetherDropdownField(
                    labelRes = R.string.aether_tor,
                    value = tor,
                    items = listOf(
                        "Off" to "off",
                        "WARP → Tor" to "chain",
                        "Tor → WARP" to "reverse",
                        "Tor only" to "only",
                    ),
                    defaultValue = "off",
                    onSelect = { tor = it },
                )
                if (torEnum != AetherTor.OFF) {
                    val bridgesUsed = torBridgesEnum != AetherTorBridges.NEVER &&
                        !(torEnum == AetherTor.CHAIN && torBridgesEnum == AetherTorBridges.AUTO)
                    if (bridgesUsed && !isTorTransportsAvailable) {
                        Text(
                            text = stringResource(R.string.aether_tor_transports_unavailable),
                            fontSize = 13.sp,
                            color = LiteColors.error,
                            fontFamily = Vazirmatn,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    AetherDropdownField(
                        labelRes = R.string.aether_tor_bridges,
                        value = torBridges,
                        items = listOf(
                            "When Tor is blocked" to "auto",
                            "From the start" to "first",
                            "Never" to "never",
                            "My own bridge lines" to "own",
                        ),
                        defaultValue = "auto",
                        onSelect = { torBridges = it },
                    )
                    if (torBridgesEnum == AetherTorBridges.AUTO || torBridgesEnum == AetherTorBridges.FIRST) {
                        AetherDropdownField(
                            labelRes = R.string.aether_tor_relays,
                            value = torRelays,
                            items = listOf(
                                "bridgedb and public relays" to "auto",
                                "Public relays only" to "only",
                                "bridgedb only" to "off",
                            ),
                            defaultValue = "auto",
                            onSelect = { torRelays = it },
                        )
                    }
                    if (torBridgesEnum == AetherTorBridges.OWN) {
                        AetherField(
                            labelRes = R.string.aether_tor_bridge_lines,
                            value = torBridgeLines,
                            onValueChange = { torBridgeLines = it },
                            singleLine = false,
                            minLines = 3,
                            placeholder = stringResource(R.string.aether_hint_tor_bridge_lines),
                        )
                    }
                }

                if (warpUsed) {
                    if (twoHops) {
                        AetherField(
                            labelRes = R.string.aether_lab_wiw_outer,
                            value = wiwOuter,
                            onValueChange = { wiwOuter = it },
                            placeholder = stringResource(R.string.aether_hint_endpoint),
                        )
                        AetherField(
                            labelRes = R.string.aether_lab_wiw_inner,
                            value = wiwInner,
                            onValueChange = { wiwInner = it },
                            placeholder = stringResource(
                                if (protocolEnum == AetherProtocol.WG_OVER_MASQUE) R.string.aether_hint_gool_peer
                                else R.string.aether_hint_endpoint,
                            ),
                        )
                    } else {
                        AetherField(
                            labelRes = R.string.aether_server,
                            value = server,
                            onValueChange = { server = it },
                            placeholder = stringResource(R.string.aether_hint_endpoint),
                        )
                        AetherField(
                            labelRes = R.string.aether_server_port,
                            value = serverPort,
                            onValueChange = { serverPort = it },
                        )
                    }
                    AetherDropdownField(
                        labelRes = R.string.aether_ip_version,
                        value = ipVersion,
                        items = listOf(
                            "IPv4" to "v4",
                            "IPv6" to "v6",
                            "IPv4 + IPv6" to "both",
                        ),
                        defaultValue = "v4",
                        onSelect = { ipVersion = it },
                    )
                }

                AetherSection(
                    titleRes = R.string.aether_lab_other_settings,
                    expanded = showOther,
                    onExpandedChange = { showOther = it },
                ) {
                    if (warpUsed) {
                        AetherField(
                            labelRes = R.string.aether_dns,
                            value = dns,
                            onValueChange = { dns = it },
                            placeholder = stringResource(R.string.aether_hint_dns),
                        )
                        AetherField(
                            labelRes = R.string.aether_exit_loc,
                            value = exitLoc,
                            onValueChange = { exitLoc = it },
                            placeholder = stringResource(R.string.aether_hint_exit_loc),
                        )
                    }
                }

                AetherField(
                    labelRes = R.string.aether_command,
                    value = command.ifBlank { builtCommand },
                    onValueChange = { command = it },
                    singleLine = false,
                    minLines = 3,
                    supportingText = if (customCommand) stringResource(R.string.aether_command_custom) else null,
                )
                if (customCommand) {
                    TextButton(onClick = { command = "" }) {
                        Text(
                            text = stringResource(R.string.aether_action_use_settings),
                            fontSize = 14.sp,
                            color = LiteColors.accent,
                            fontFamily = Vazirmatn
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 3.2.13: save over the settings this editor opened, so
                // what it does not show — a field hidden by the conditions
                // above, or a value an imported aether:// link carried —
                // survives an edit instead of being silently dropped.
                onSave(name.trim().ifEmpty { "Aether" }, edited)
            }) {
                Text(
                    text = stringResource(confirmRes),
                    fontSize = 14.sp,
                    color = LiteColors.accent,
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
    if (showKeys) {
        AetherKeysDialog(vm = vm, profiles = profiles, onDismiss = { showKeys = false })
    }
}

/**
 * The WARP keys page (3.2.19, PattNG ServerAetherKeysActivity, faithful): the kind of keys, the API
 * server's address, server name and host, fragment and ECH of the calls to the API, the fingerprint,
 * the exit node the renewal dials out through, its finalMask and dialMode, the command the renewal
 * runs, and the log — the keys in use are its first lines. A dialog, not a screen, so the Summit UI
 * stays. The settings are the page's alone — no profile's settings reach them.
 */
@Composable
private fun AetherKeysDialog(vm: LiteViewModel, profiles: List<Profile>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.aetherKeysSettings.collectAsState()
    val log by vm.aetherKeysLog.collectAsState()
    val renewing by vm.aetherKeysRenewing.collectAsState()
    val notice by vm.aetherKeysNotice.collectAsState()
    val coreAvailable by vm.aetherKeysCoreAvailable.collectAsState()
    val connection by vm.connection.collectAsState()
    LaunchedEffect(Unit) { vm.loadAetherKeys() }
    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearAetherKeysNotice()
        }
    }
    val enabled = !renewing
    val blocked = connection.state == ConnState.CONNECTED || connection.state == ConnState.CONNECTING
    val freedom = settings.exitNode.isBlank()
    val builtCommand = AetherKeys.builtCommand(settings)
    val customCommand = AetherKeys.isCustom(settings)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.aether_keys_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.aether_keys_lab_kind),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiteColors.accent,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Column(modifier = Modifier.selectableGroup().padding(bottom = 8.dp)) {
                    for (kind in AetherKeyKind.entries) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.kind == kind,
                                    onClick = {
                                        if (enabled) vm.setAetherKeysSettings(settings.copy(kind = kind))
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.kind == kind,
                                onClick = null,
                                enabled = enabled,
                                colors = RadioButtonDefaults.colors(selectedColor = LiteColors.accent),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = aetherKeyKindLabel(kind),
                                fontSize = 14.sp,
                                color = LiteColors.text,
                                fontFamily = Vazirmatn,
                            )
                        }
                    }
                }

                AetherDropdownField(
                    labelRes = R.string.aether_keys_enroll_address,
                    value = settings.enrollAddress,
                    items = AETHER_API_NAME_OPTIONS.map { it to it },
                    editable = true,
                    defaultOption = stringResource(R.string.preset_default),
                    placeholder = stringResource(R.string.aether_keys_hint_enroll_address),
                    supportingText = if (settings.enrollAddress.isBlank()) stringResource(R.string.preset_default) else null,
                    enabled = enabled,
                    onSelect = { vm.setAetherKeysSettings(settings.copy(enrollAddress = it)) },
                )
                AetherDropdownField(
                    labelRes = R.string.aether_keys_api_sni,
                    value = settings.apiSni,
                    items = AETHER_API_NAME_OPTIONS.map { it to it },
                    editable = true,
                    defaultOption = stringResource(R.string.preset_default),
                    placeholder = stringResource(R.string.aether_keys_hint_api_sni),
                    supportingText = if (settings.apiSni.isBlank()) stringResource(R.string.preset_default) else null,
                    enabled = enabled,
                    onSelect = { vm.setAetherKeysSettings(settings.copy(apiSni = it)) },
                )
                AetherDropdownField(
                    labelRes = R.string.aether_keys_api_host,
                    value = settings.apiHost,
                    items = AETHER_API_NAME_OPTIONS.map { it to it },
                    editable = true,
                    defaultOption = stringResource(R.string.preset_default),
                    placeholder = stringResource(R.string.aether_keys_hint_api_host),
                    supportingText = buildString {
                        if (settings.apiHost.isBlank()) append(stringResource(R.string.preset_default))
                        val hint = stringResource(R.string.aether_keys_hint_api_host)
                        if (isNotEmpty()) append("\n")
                        append(hint)
                    },
                    enabled = enabled,
                    onSelect = { vm.setAetherKeysSettings(settings.copy(apiHost = it)) },
                )

                AetherSwitchRow(
                    labelRes = R.string.aether_fragment,
                    summaryRes = R.string.aether_keys_hint_fragment,
                    checked = settings.fragment,
                    enabled = enabled,
                    onChange = { vm.setAetherKeysSettings(settings.copy(fragment = it)) },
                )
                if (settings.fragment) {
                    AetherField(
                        labelRes = R.string.aether_keys_fragment_size,
                        value = settings.fragmentSize,
                        onValueChange = { vm.setAetherKeysSettings(settings.copy(fragmentSize = it)) },
                        placeholder = stringResource(R.string.aether_hint_fragment_size),
                        enabled = enabled,
                    )
                    AetherField(
                        labelRes = R.string.aether_keys_fragment_delay,
                        value = settings.fragmentDelay,
                        onValueChange = { vm.setAetherKeysSettings(settings.copy(fragmentDelay = it)) },
                        placeholder = stringResource(R.string.aether_hint_fragment_delay),
                        enabled = enabled,
                    )
                }
                AetherSwitchRow(
                    labelRes = R.string.aether_ech,
                    summaryRes = R.string.aether_keys_hint_ech,
                    checked = settings.ech,
                    enabled = enabled,
                    onChange = { vm.setAetherKeysSettings(settings.copy(ech = it)) },
                )
                if (settings.ech) {
                    AetherDropdownField(
                        labelRes = R.string.aether_keys_ech_dns,
                        value = settings.echDns,
                        items = AETHER_ECH_DNS_OPTIONS.map { it to it },
                        editable = true,
                        enabled = enabled,
                        onSelect = { vm.setAetherKeysSettings(settings.copy(echDns = it)) },
                    )
                    AetherDropdownField(
                        labelRes = R.string.aether_keys_ech_domain,
                        value = settings.echDomain,
                        items = AETHER_ECH_DOMAIN_OPTIONS.map { it to it },
                        editable = true,
                        enabled = enabled,
                        onSelect = { vm.setAetherKeysSettings(settings.copy(echDomain = it)) },
                    )
                }
                AetherDropdownField(
                    labelRes = R.string.aether_fingerprint,
                    value = settings.fingerprint.type,
                    items = listOf(
                        "Chrome" to "chrome",
                        "Chrome 120" to "chrome-120",
                        "Firefox" to "firefox",
                        "Semi-Python" to "semi-python",
                        "Go" to "go",
                    ),
                    defaultValue = "chrome",
                    enabled = enabled,
                    onSelect = { vm.setAetherKeysSettings(settings.copy(fingerprint = AetherFingerprint.fromString(it))) },
                )
                AetherPickerRow(
                    labelRes = R.string.aether_exit_node,
                    options = profiles.filter { !it.isAether }.map { it.name },
                    selected = settings.exitNode,
                    noneLabel = stringResource(R.string.aether_exit_node_freedom),
                    supportingText = if (settings.exitNode.isNotBlank()) stringResource(R.string.aether_hint_exit_node) else null,
                    enabled = enabled,
                    onSelect = { vm.setAetherKeysSettings(settings.copy(exitNode = it)) },
                )
                AetherPresetField(
                    labelRes = R.string.aether_keys_final_mask,
                    value = settings.finalMask,
                    onValueChange = { vm.setAetherKeysSettings(settings.copy(finalMask = it)) },
                    enabled = enabled && freedom,
                )
                AetherDropdownField(
                    labelRes = R.string.aether_keys_dial_mode,
                    value = settings.dialMode,
                    items = emptyList(),
                    editable = true,
                    defaultOption = stringResource(R.string.preset_default),
                    supportingText = if (settings.dialMode.isBlank()) stringResource(R.string.preset_default) else null,
                    enabled = enabled && freedom,
                    onSelect = { vm.setAetherKeysSettings(settings.copy(dialMode = it)) },
                )

                if (blocked) {
                    Text(
                        text = stringResource(R.string.aether_keys_renew_blocked),
                        fontSize = 13.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                if (!coreAvailable) {
                    Text(
                        text = stringResource(R.string.aether_unsupported_abi),
                        fontSize = 13.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }

                AetherField(
                    labelRes = R.string.aether_command,
                    value = settings.command.ifBlank { builtCommand },
                    onValueChange = { vm.setAetherKeysCommand(it) },
                    singleLine = false,
                    minLines = 3,
                    supportingText = if (customCommand) stringResource(R.string.aether_command_custom) else null,
                    enabled = enabled,
                )
                if (customCommand) {
                    TextButton(
                        onClick = { vm.useAetherKeysSettings() },
                        enabled = enabled,
                    ) {
                        Text(
                            text = stringResource(R.string.aether_action_use_settings),
                            fontSize = 14.sp,
                            color = LiteColors.accent,
                            fontFamily = Vazirmatn
                        )
                    }
                }

                AetherLogPanel(log = log)
            }
        },
        confirmButton = {
            if (renewing) {
                Button(
                    onClick = { vm.cancelAetherKeysRenewal() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiteColors.accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.sub_import_cancel),
                        fontSize = 14.sp,
                        fontFamily = Vazirmatn
                    )
                }
            } else {
                Button(
                    onClick = { vm.renewAetherKeys() },
                    enabled = coreAvailable && !blocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiteColors.accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.aether_keys_renew),
                        fontSize = 14.sp,
                        fontFamily = Vazirmatn
                    )
                }
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

/**
 * A dropdown field in the shape of [AetherField], as PattNG's FormDropdownField: a label, the
 * picked value (or the default's label when the value is blank), and a menu of options. [editable]
 * lets the value be typed free-form; [defaultOption] is the menu item that clears the value, shown
 * as the supporting text while it is blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AetherDropdownField(
    labelRes: Int,
    value: String,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    defaultValue: String = "",
    editable: Boolean = false,
    defaultOption: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (editable) {
        value
    } else {
        items.firstOrNull { it.second == value }?.first
            ?: items.firstOrNull { it.second == defaultValue }?.first
            ?: value
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = { if (editable) onSelect(it) },
            label = {
                Text(
                    text = stringResource(labelRes),
                    fontSize = 13.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            },
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn
                    )
                }
            },
            supportingText = supportingText?.let {
                {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn
                    )
                }
            },
            readOnly = !editable,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Vazirmatn,
                fontSize = 14.sp,
                color = LiteColors.text,
                textDirection = TextDirection.Ltr
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LiteColors.hairlineStrong,
                unfocusedBorderColor = LiteColors.hairline,
                cursorColor = LiteColors.accent,
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            shape = RoundedCornerShape(10.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (defaultOption != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = defaultOption,
                            fontSize = 13.sp,
                            fontFamily = Vazirmatn,
                            color = LiteColors.text
                        )
                    },
                    onClick = { onSelect(""); expanded = false },
                )
            }
            items.forEach { (label, itemValue) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontFamily = Vazirmatn,
                            color = LiteColors.text
                        )
                    },
                    onClick = { onSelect(itemValue); expanded = false },
                )
            }
        }
    }
}

/**
 * A text field with a preset menu, as PattNG's FinalMaskField: the raw JSON stays editable, and the
 * trailing arrow offers the presets shipped in res/values/arrays.xml. Blank means the core's
 * default, shown as the supporting text.
 */
@Composable
private fun AetherPresetField(
    labelRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val presetNames = stringArrayResource(R.array.final_mask_preset_names)
    val presetValues = stringArrayResource(R.array.final_mask_preset_values)
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        AetherField(
            labelRes = labelRes,
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            minLines = 1,
            supportingText = if (value.isBlank()) stringResource(R.string.preset_default) else null,
            enabled = enabled,
            trailingIcon = {
                Box(
                    modifier = Modifier.clickable(enabled = enabled) { expanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    ChevronDownIcon(size = 16.dp, color = LiteColors.textSecondary)
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presetNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            fontSize = 13.sp,
                            fontFamily = Vazirmatn,
                            color = LiteColors.text
                        )
                    },
                    onClick = {
                        if (index < presetValues.size) onValueChange(presetValues[index])
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A collapsible section in the shape of the settings screen's SettingsGroup: a surface card with a
 * hairline border, a header row with a chevron that turns as it opens, and the content below it.
 * [summary] is the one-line state of the section, shown under its title while it is closed.
 */
@Composable
private fun AetherSection(
    titleRes: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(LiteColors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, LiteColors.hairline, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                )
                if (!expanded && summary != null) {
                    Text(
                        text = summary,
                        fontSize = 12.sp,
                        color = LiteColors.textMuted,
                        fontFamily = Vazirmatn,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 0f else -90f },
            ) {
                ChevronDownIcon(size = 16.dp, color = LiteColors.textSecondary)
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                content = content,
            )
        }
    }
}

/** A checkbox row, as PattNG's rows of the CDN edge lists. */
@Composable
private fun AetherCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = LiteColors.text,
            fontFamily = Vazirmatn,
        )
    }
}

/**
 * The log panel, as PattNG's AetherLogPanel: a "Log" header with a Copy action, and a fixed-height
 * scrolling list of the log lines in monospace. The newest line is always in view.
 */
@Composable
private fun AetherLogPanel(log: List<String>) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.aether_log_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn,
            )
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("log", log.joinToString("\n")))
                    Toast.makeText(context, context.getString(R.string.aether_log_copy), Toast.LENGTH_SHORT).show()
                },
                enabled = log.isNotEmpty(),
            ) {
                Text(
                    text = stringResource(R.string.aether_log_copy),
                    fontSize = 13.sp,
                    color = LiteColors.accent,
                    fontFamily = Vazirmatn
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(LiteColors.surface2, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (log.isEmpty()) {
                Text(
                    text = stringResource(R.string.aether_keys_log_empty),
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                )
            } else {
                LazyColumn(state = listState) {
                    items(log) { line ->
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LiteColors.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Dropdown picker row (3.2.11, PattNG ExitNodeField): label + the picked
 * value; tap opens a menu with "none" first, then the options. A saved
 * value that is no longer among the options is still shown as-is. */
@Composable
private fun AetherPickerRow(
    labelRes: Int,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    noneLabel: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val none = noneLabel ?: stringResource(R.string.aether_default, "—")
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = { expanded = true }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(labelRes),
                fontSize = 13.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = selected.ifEmpty { none },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) LiteColors.accent else LiteColors.textMuted,
                fontFamily = Vazirmatn
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                fontSize = 12.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = none,
                        fontSize = 13.sp,
                        fontFamily = Vazirmatn,
                        color = LiteColors.text
                    )
                },
                onClick = { onSelect(""); expanded = false }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontSize = 13.sp,
                            fontFamily = Vazirmatn,
                            color = LiteColors.text
                        )
                    },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

/** Label + switch row, with an optional summary line under the label. */
@Composable
private fun AetherSwitchRow(
    labelRes: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    summaryRes: Int? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(labelRes),
                fontSize = 13.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn
            )
            if (summaryRes != null) {
                Text(
                    text = stringResource(summaryRes),
                    fontSize = 12.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LiteColors.accent,
                checkedTrackColor = LiteColors.accent.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun AetherField(
    labelRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = stringResource(labelRes),
                fontSize = 13.sp,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn
            )
        },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = LiteColors.textMuted,
                    fontFamily = Vazirmatn
                )
            }
        },
        supportingText = supportingText?.let {
            {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn
                )
            }
        },
        trailingIcon = trailingIcon,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = TextStyle(
            fontFamily = Vazirmatn,
            fontSize = 14.sp,
            color = LiteColors.text,
            textDirection = TextDirection.Ltr
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LiteColors.hairlineStrong,
            unfocusedBorderColor = LiteColors.hairline,
            cursorColor = LiteColors.accent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}
