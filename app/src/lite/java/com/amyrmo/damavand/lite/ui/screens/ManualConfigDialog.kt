package com.amyrmo.damavand.lite.ui.screens

import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.XrayConfig
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.components.ChevronDownIcon
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Manual per-protocol editor (3.2.0, PattNG): build a config link field
 * by field instead of pasting one. Covers VLESS, VMess, Trojan,
 * Shadowsocks, SOCKS, HTTP, Hysteria2, WireGuard, plus a raw Xray JSON
 * profile (custom://). The built link goes through the same parser as
 * pasted links, so an invalid field set fails loudly instead of
 * connecting broken.
 *
 * 3.2.8: the same form is the EDIT dialog too (PattNG's
 * ServerVlessActivity etc. edit field-by-field — the old Edit dialog
 * was just a raw link box). [ManualConfigDialog] adds a fresh config,
 * [EditConfigDialog] edits one in place, with the protocol locked
 * (PattNG edits one protocol per screen) and the fields pre-filled
 * from the parsed link.
 *
 * 3.2.19: the changeable parts sit in collapsible sections — the core
 * fields (name, address, port, identity) stay visible, and transport,
 * security, ECH/pinning and advanced fields hide behind a section that
 * opens by itself when it already holds a non-default value.
 */
@Composable
fun ManualConfigDialog(vm: LiteViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ManualConfigEditor(
        initialProto = "vless",
        initialFields = emptyMap(),
        titleRes = R.string.manual_title,
        confirmRes = R.string.manual_add,
        allowProtoChange = true,
        onSave = { _, link, done ->
            vm.addManualLink(link) { ok ->
                if (ok) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.manual_added),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                done(ok)
            }
        },
        onDismiss = onDismiss
    )
}

/** Edit one existing profile, field by field (3.2.8, PattNG). */
@Composable
fun EditConfigDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (String, String, (Boolean) -> Unit) -> Unit
) {
    val parsed = remember(profile.link) { XrayConfig.parse(profile.link) } ?: run {
        // Unparseable — the caller falls back to the raw link dialog.
        onDismiss()
        return
    }
    ManualConfigEditor(
        initialProto = parsed.proto,
        initialFields = fieldsFromParsed(parsed, profile.name),
        titleRes = R.string.server_edit_title,
        confirmRes = R.string.server_edit_save,
        allowProtoChange = false,
        onSave = onSave,
        onDismiss = onDismiss
    )
}

/** The parsed link → the editor's field map (PattNG's field set). */
private fun fieldsFromParsed(p: XrayConfig.Parsed, name: String): Map<String, String> {
    val f = mutableMapOf<String, String>()
    f["name"] = name
    f["addr"] = p.addr
    if (p.port > 0) f["port"] = p.port.toString()
    when (p.proto) {
        "vless", "vmess" -> {
            f["uuid"] = p.id
            f["security"] = p.tls.ifEmpty { "tls" }
            f["net"] = p.net.ifEmpty { "tcp" }
            f["sni"] = p.sni
            f["path"] = p.path
            f["host"] = p.host
            f["fp"] = p.fp
            f["allowInsecure"] = if (p.allowInsecure) "1" else "0"
            f["alpn"] = p.alpn
            f["cs"] = p.cipherSuites
            f["pbk"] = p.realityPublicKey
            f["sid"] = p.realityShortId
            f["flow"] = p.flow
            // 3.2.9 (PattNG, the rest): ECH + pinning + ML-DSA.
            f["ech"] = p.echConfigList
            f["echOutbound"] = p.echOutbound
            f["vcn"] = p.verifyPeerCertByName
            f["pcs"] = p.pinnedCA256
            f["spx"] = p.spiderX
            f["pqv"] = p.mldsa65Verify
        }
        "trojan" -> {
            f["password"] = p.id
            f["security"] = p.tls.ifEmpty { "tls" }
            f["sni"] = p.sni
            f["fp"] = p.fp
            f["allowInsecure"] = if (p.allowInsecure) "1" else "0"
            f["alpn"] = p.alpn
            f["cs"] = p.cipherSuites
            f["pbk"] = p.realityPublicKey
            f["sid"] = p.realityShortId
            f["ech"] = p.echConfigList
            f["echOutbound"] = p.echOutbound
            f["vcn"] = p.verifyPeerCertByName
            f["pcs"] = p.pinnedCA256
            f["spx"] = p.spiderX
            f["pqv"] = p.mldsa65Verify
        }
        "ss" -> {
            f["method"] = p.id.substringBefore(":")
            f["password"] = p.id.substringAfter(":", "")
        }
        "socks", "http" -> {
            f["username"] = p.id.substringBefore(":")
            f["password"] = p.id.substringAfter(":", "")
        }
        "hysteria2" -> {
            f["password"] = p.id
            f["sni"] = p.sni
            f["insecure"] = p.hyInsecure
            // 1.0.2: salamander obfs password — without it, editing
            // an imported obfs link would silently drop it on save.
            f["obfsPassword"] = p.obfsPassword
            // 3.2.9: ECH + pinning ride on Hysteria2 too (PattNG).
            f["ech"] = p.echConfigList
            f["echOutbound"] = p.echOutbound
            f["vcn"] = p.verifyPeerCertByName
            f["pcs"] = p.pinnedCA256
        }
        "wg" -> {
            f["privateKey"] = p.id
            f["publicKey"] = p.wgPublicKey
            f["allowedIps"] = p.wgAllowedIps
            f["localAddress"] = p.wgAddress
            f["mtu"] = p.wgMtu
        }
    }
    return f
}

@Composable
private fun ManualConfigEditor(
    initialProto: String,
    initialFields: Map<String, String>,
    titleRes: Int,
    confirmRes: Int,
    allowProtoChange: Boolean,
    onSave: (String, String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var proto by remember { mutableStateOf(initialProto) }
    val fields = remember {
        mutableStateMapOf<String, String>().apply { putAll(initialFields) }
    }
    var error by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun f(key: String): String = fields[key].orEmpty()
    fun setF(key: String, v: String) { fields[key] = v }

    // 3.2.19: each section opens by itself when it already holds a
    // non-default value (an edit of a link that set one), and stays
    // closed on a fresh add.
    var showTransport by remember {
        mutableStateOf(
            (f("security").isNotBlank() && f("security") != "tls") ||
                (f("net").isNotBlank() && f("net") != "tcp") ||
                f("sni").isNotBlank() || f("path").isNotBlank() || f("host").isNotBlank()
        )
    }
    var showSecurity by remember {
        mutableStateOf(
            f("fp").isNotBlank() || f("allowInsecure") == "1" || f("alpn").isNotBlank() ||
                f("cs").isNotBlank() || f("pbk").isNotBlank() || f("sid").isNotBlank() ||
                f("spx").isNotBlank() || f("pqv").isNotBlank() || f("ech").isNotBlank() ||
                f("echOutbound").isNotBlank() || f("vcn").isNotBlank() || f("pcs").isNotBlank()
        )
    }
    var showEch by remember {
        mutableStateOf(
            f("ech").isNotBlank() || f("echOutbound").isNotBlank() ||
                f("vcn").isNotBlank() || f("pcs").isNotBlank()
        )
    }
    var showAdvanced by remember {
        mutableStateOf(
            f("allowedIps").isNotBlank() || f("localAddress").isNotBlank() || f("mtu").isNotBlank()
        )
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = stringResource(titleRes),
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = LiteColors.text,
                fontFamily = Vazirmatn
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Protocol picker (add only — edit locks the protocol,
                // like PattNG's per-protocol edit screens).
                if (allowProtoChange) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (p in listOf(
                            "vless", "vmess", "trojan", "ss", "socks",
                            "http", "hysteria2", "wg", "json"
                        )) {
                            ManualChip(
                                label = when (p) {
                                    "ss" -> "Shadowsocks"
                                    "wg" -> "WireGuard"
                                    "json" -> "JSON"
                                    else -> p.replaceFirstChar { it.uppercase() }
                                },
                                selected = proto == p,
                                onClick = { proto = p; error = false }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (proto == "json") {
                    ManualField(
                        labelRes = R.string.manual_name,
                        value = f("name"),
                        onValueChange = { setF("name", it) }
                    )
                    ManualField(
                        labelRes = R.string.manual_json,
                        value = f("json"),
                        onValueChange = { setF("json", it) },
                        singleLine = false,
                        minLines = 6
                    )
                    Text(
                        text = stringResource(R.string.manual_json_hint),
                        fontSize = 12.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn
                    )
                } else {
                    ManualField(
                        labelRes = R.string.manual_name,
                        value = f("name"),
                        onValueChange = { setF("name", it) }
                    )
                    ManualField(
                        labelRes = R.string.manual_address,
                        value = f("addr"),
                        onValueChange = { setF("addr", it) }
                    )
                    ManualField(
                        labelRes = R.string.manual_port,
                        value = f("port"),
                        onValueChange = { setF("port", it) }
                    )
                    when (proto) {
                        "vless", "vmess" -> {
                            ManualField(
                                labelRes = R.string.manual_uuid,
                                value = f("uuid"),
                                onValueChange = { setF("uuid", it) }
                            )
                            ManualSection(
                                titleRes = R.string.manual_section_transport,
                                expanded = showTransport,
                                onExpandedChange = { showTransport = it },
                            ) {
                                ManualChipRow(
                                    labelRes = R.string.manual_security,
                                    options = listOf("none", "tls", "reality"),
                                    selected = f("security").ifEmpty { "tls" },
                                    onSelect = { setF("security", it) }
                                )
                                ManualChipRow(
                                    labelRes = R.string.manual_network,
                                    options = listOf("tcp", "ws", "grpc", "xhttp"),
                                    selected = f("net").ifEmpty { "tcp" },
                                    onSelect = { setF("net", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_sni,
                                    value = f("sni"),
                                    onValueChange = { setF("sni", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_path,
                                    value = f("path"),
                                    onValueChange = { setF("path", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_host,
                                    value = f("host"),
                                    onValueChange = { setF("host", it) }
                                )
                            }
                            // 3.2.7 (PattNG stream security): fingerprint,
                            // allow-insecure, ALPN, cipher suites, and
                            // Reality's public key + short id. Hidden when
                            // the security is "none" — TlsFields renders
                            // nothing then either.
                            if (f("security").ifEmpty { "tls" } != "none") {
                                ManualSection(
                                    titleRes = R.string.manual_section_security,
                                    expanded = showSecurity,
                                    onExpandedChange = { showSecurity = it },
                                ) {
                                    TlsFields(
                                        security = f("security").ifEmpty { "tls" },
                                        f = ::f,
                                        setF = ::setF
                                    )
                                }
                            }
                        }
                        "trojan" -> {
                            ManualField(
                                labelRes = R.string.manual_password,
                                value = f("password"),
                                onValueChange = { setF("password", it) }
                            )
                            ManualSection(
                                titleRes = R.string.manual_section_transport,
                                expanded = showTransport,
                                onExpandedChange = { showTransport = it },
                            ) {
                                ManualChipRow(
                                    labelRes = R.string.manual_security,
                                    options = listOf("tls", "reality"),
                                    selected = f("security").ifEmpty { "tls" },
                                    onSelect = { setF("security", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_sni,
                                    value = f("sni"),
                                    onValueChange = { setF("sni", it) }
                                )
                            }
                            // 3.2.7 (PattNG stream security).
                            ManualSection(
                                titleRes = R.string.manual_section_security,
                                expanded = showSecurity,
                                onExpandedChange = { showSecurity = it },
                            ) {
                                TlsFields(
                                    security = f("security").ifEmpty { "tls" },
                                    f = ::f,
                                    setF = ::setF
                                )
                            }
                        }
                        "ss" -> {
                            ManualField(
                                labelRes = R.string.manual_password,
                                value = f("password"),
                                onValueChange = { setF("password", it) }
                            )
                            ManualField(
                                labelRes = R.string.manual_method,
                                value = f("method").ifEmpty { "aes-256-gcm" },
                                onValueChange = { setF("method", it) }
                            )
                        }
                        "socks", "http" -> {
                            ManualField(
                                labelRes = R.string.manual_username,
                                value = f("username"),
                                onValueChange = { setF("username", it) }
                            )
                            ManualField(
                                labelRes = R.string.manual_password,
                                value = f("password"),
                                onValueChange = { setF("password", it) }
                            )
                        }
                        "hysteria2" -> {
                            ManualField(
                                labelRes = R.string.manual_password,
                                value = f("password"),
                                onValueChange = { setF("password", it) }
                            )
                            ManualField(
                                labelRes = R.string.manual_sni,
                                value = f("sni"),
                                onValueChange = { setF("sni", it) }
                            )
                            ManualChipRow(
                                labelRes = R.string.manual_insecure,
                                options = listOf("0", "1"),
                                selected = f("insecure").ifEmpty { "0" },
                                onSelect = { setF("insecure", it) }
                            )
                            // 1.0.2: salamander obfs password (PattNG
                            // Hysteria2Fmt writes obfs=salamander +
                            // obfs-password when it is set).
                            ManualField(
                                labelRes = R.string.manual_obfs_password,
                                value = f("obfsPassword"),
                                onValueChange = { setF("obfsPassword", it) }
                            )
                            // 3.2.9 (PattNG): ECH + pinning ride on
                            // Hysteria2 too (its blank security is TLS).
                            ManualSection(
                                titleRes = R.string.manual_section_ech,
                                expanded = showEch,
                                onExpandedChange = { showEch = it },
                            ) {
                                ManualField(
                                    labelRes = R.string.manual_ech,
                                    value = f("ech"),
                                    onValueChange = { setF("ech", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_ech_outbound,
                                    value = f("echOutbound"),
                                    onValueChange = { setF("echOutbound", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_vcn,
                                    value = f("vcn"),
                                    onValueChange = { setF("vcn", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_pcs,
                                    value = f("pcs"),
                                    onValueChange = { setF("pcs", it) }
                                )
                            }
                        }
                        "wg" -> {
                            ManualField(
                                labelRes = R.string.manual_private_key,
                                value = f("privateKey"),
                                onValueChange = { setF("privateKey", it) }
                            )
                            ManualField(
                                labelRes = R.string.manual_public_key,
                                value = f("publicKey"),
                                onValueChange = { setF("publicKey", it) }
                            )
                            ManualSection(
                                titleRes = R.string.manual_section_advanced,
                                expanded = showAdvanced,
                                onExpandedChange = { showAdvanced = it },
                            ) {
                                ManualField(
                                    labelRes = R.string.manual_allowed_ips,
                                    value = f("allowedIps").ifEmpty { "0.0.0.0/0,::/0" },
                                    onValueChange = { setF("allowedIps", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_local_address,
                                    value = f("localAddress"),
                                    onValueChange = { setF("localAddress", it) }
                                )
                                ManualField(
                                    labelRes = R.string.manual_mtu,
                                    value = f("mtu"),
                                    onValueChange = { setF("mtu", it) }
                                )
                            }
                        }
                    }
                }
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.manual_invalid),
                        fontSize = 12.sp,
                        color = LiteColors.error,
                        fontFamily = Vazirmatn
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val link = buildManualLink(proto, fields.toMap()) ?: run {
                        error = true
                        return@TextButton
                    }
                    saving = true
                    onSave(f("name"), link) { ok ->
                        saving = false
                        if (ok) {
                            onDismiss()
                        } else {
                            error = true
                        }
                    }
                },
                enabled = !saving
            ) {
                Text(
                    text = stringResource(confirmRes),
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) {
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
 * A collapsible section (3.2.19, in the shape of the settings
 * screen's SettingsGroup): a surface card with a hairline border, a
 * header row with a chevron that turns as it opens, and the content
 * below it. The core fields stay outside the sections, always
 * visible; the changeable parts hide behind one of these.
 */
@Composable
private fun ManualSection(
    titleRes: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
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
            Text(
                text = stringResource(titleRes),
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = LiteColors.textSecondary,
                fontFamily = Vazirmatn,
                modifier = Modifier.weight(1f),
            )
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

@Composable
private fun ManualField(
    labelRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1
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

/**
 * Stream-security fields (3.2.7, PattNG's CommonStreamSecurityFields):
 * fingerprint picker, allow-insecure (TLS only), ALPN + cipher suites
 * (TLS only), Reality public key + short id (Reality only).
 */
@Composable
private fun TlsFields(
    security: String,
    f: (String) -> String,
    setF: (String, String) -> Unit
) {
    if (security == "none") return
    ManualCycleRow(
        labelRes = R.string.manual_fingerprint,
        options = listOf(
            "", "chrome", "firefox", "safari", "ios", "android",
            "edge", "qq", "random", "randomized"
        ),
        selected = f("fp"),
        onSelect = { setF("fp", it) }
    )
    if (security == "tls") {
        ManualSwitchRow(
            labelRes = R.string.manual_allow_insecure,
            checked = f("allowInsecure") == "1",
            onChange = { setF("allowInsecure", if (it) "1" else "0") }
        )
        ManualField(
            labelRes = R.string.manual_alpn,
            value = f("alpn"),
            onValueChange = { setF("alpn", it) }
        )
        ManualField(
            labelRes = R.string.manual_cipher_suites,
            value = f("cs"),
            onValueChange = { setF("cs", it) }
        )
        // 3.2.9 (PattNG, the rest): ECH config list + the outbound
        // the ECH query goes through (raw JSON), verify peer cert by
        // name, pinned CA SHA-256.
        ManualField(
            labelRes = R.string.manual_ech,
            value = f("ech"),
            onValueChange = { setF("ech", it) }
        )
        ManualField(
            labelRes = R.string.manual_ech_outbound,
            value = f("echOutbound"),
            onValueChange = { setF("echOutbound", it) }
        )
        ManualField(
            labelRes = R.string.manual_vcn,
            value = f("vcn"),
            onValueChange = { setF("vcn", it) }
        )
        ManualField(
            labelRes = R.string.manual_pcs,
            value = f("pcs"),
            onValueChange = { setF("pcs", it) }
        )
    }
    if (security == "reality") {
        ManualField(
            labelRes = R.string.manual_reality_pbk,
            value = f("pbk"),
            onValueChange = { setF("pbk", it) }
        )
        ManualField(
            labelRes = R.string.manual_reality_sid,
            value = f("sid"),
            onValueChange = { setF("sid", it) }
        )
        ManualField(
            labelRes = R.string.manual_spx,
            value = f("spx"),
            onValueChange = { setF("spx", it) }
        )
        ManualField(
            labelRes = R.string.manual_pqv,
            value = f("pqv"),
            onValueChange = { setF("pqv", it) }
        )
    }
}

/** Tap-to-cycle row (like the Settings screen's CycleRow). */
@Composable
private fun ManualCycleRow(
    labelRes: Int,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                val idx = options.indexOf(selected).takeIf { it >= 0 } ?: 0
                onSelect(options[(idx + 1).mod(options.size)])
            })
            .padding(bottom = 8.dp),
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
            text = selected.ifEmpty { stringResource(R.string.manual_fingerprint_default) },
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = LiteColors.accent,
            fontFamily = Vazirmatn
        )
    }
}

/** Label + switch row. */
@Composable
private fun ManualSwitchRow(
    labelRes: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LiteColors.accent,
                checkedTrackColor = LiteColors.accent.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun ManualChipRow(
    labelRes: Int,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(labelRes),
            fontSize = 13.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (opt in options) {
                ManualChip(
                    label = opt,
                    selected = selected == opt,
                    onClick = { onSelect(opt) }
                )
            }
        }
    }
}

@Composable
private fun ManualChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(
                if (selected) LiteColors.accent
                else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (selected) LiteColors.accent else LiteColors.hairlineStrong,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (selected) androidx.compose.ui.graphics.Color.White
            else LiteColors.text,
            fontFamily = Vazirmatn
        )
    }
}

private fun enc(s: String): String =
    URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** Build a share link from the editor fields; null when required fields are missing. */
private fun buildManualLink(proto: String, f: Map<String, String>): String? {
    val name = f["name"].orEmpty().trim()
    // A share link carries an IPv6 literal in brackets ("[2001:db8::1]:443").
    val rawAddr = f["addr"].orEmpty().trim().removePrefix("[").removeSuffix("]")
    val addr = if (':' in rawAddr) "[$rawAddr]" else rawAddr
    val port = f["port"].orEmpty().trim()
    val frag = if (name.isEmpty()) "" else "#" + enc(name)
    fun query(vararg pairs: Pair<String, String>): String {
        val q = pairs.filter { it.second.isNotEmpty() }
            .joinToString("&") { "${it.first}=${enc(it.second)}" }
        return if (q.isEmpty()) "" else "?$q"
    }
    return when (proto) {
        "vless" -> {
            val uuid = f["uuid"].orEmpty().trim()
            if (uuid.isEmpty() || addr.isEmpty() || port.isEmpty()) return null
            "vless://$uuid@$addr:$port" + query(
                "security" to f["security"].orEmpty().ifEmpty { "tls" },
                "sni" to f["sni"].orEmpty(),
                "type" to f["net"].orEmpty().ifEmpty { "tcp" },
                "path" to f["path"].orEmpty(),
                "host" to f["host"].orEmpty(),
                // 3.2.7 (PattNG share link): fingerprint, allow-insecure,
                // ALPN, cipher suites, Reality key/id, flow.
                // 3.2.9 (the rest): ECH, ECH outbound, verify-by-name,
                // pinned CA, spiderX, ML-DSA verify.
                "fp" to f["fp"].orEmpty(),
                "allowInsecure" to if (f["allowInsecure"] == "1") "1" else "",
                "alpn" to f["alpn"].orEmpty(),
                "cs" to f["cs"].orEmpty(),
                "pbk" to f["pbk"].orEmpty(),
                "sid" to f["sid"].orEmpty(),
                "flow" to f["flow"].orEmpty(),
                "ech" to f["ech"].orEmpty(),
                "echOutbound" to f["echOutbound"].orEmpty(),
                "vcn" to f["vcn"].orEmpty(),
                "pcs" to f["pcs"].orEmpty(),
                "spx" to f["spx"].orEmpty(),
                "pqv" to f["pqv"].orEmpty(),
            ) + frag
        }
        "vmess" -> {
            val uuid = f["uuid"].orEmpty().trim()
            if (uuid.isEmpty() || addr.isEmpty() || port.isEmpty()) return null
            val j = JSONObject().apply {
                put("v", "2")
                put("ps", name.ifEmpty { "$addr:$port" })
                put("add", rawAddr) // VMess JSON: the bare address
                put("port", port)
                put("id", uuid)
                put("aid", "0")
                put("scy", "auto")
                put("net", f["net"].orEmpty().ifEmpty { "tcp" })
                put("type", "none")
                put("host", f["host"].orEmpty())
                put("path", f["path"].orEmpty())
                put("tls", f["security"].orEmpty().ifEmpty { "tls" })
                put("sni", f["sni"].orEmpty())
                // 3.2.7: fingerprint + ALPN ride in the VMess JSON too.
                // 3.2.9: ECH + pinning + ML-DSA + spiderX ride too.
                put("fp", f["fp"].orEmpty())
                put("alpn", f["alpn"].orEmpty())
                put("ech", f["ech"].orEmpty())
                put("echOutbound", f["echOutbound"].orEmpty())
                put("vcn", f["vcn"].orEmpty())
                put("pcs", f["pcs"].orEmpty())
                put("pqv", f["pqv"].orEmpty())
                put("spx", f["spx"].orEmpty())
            }
            "vmess://" + Base64.encodeToString(
                j.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            ) + frag
        }
        "trojan" -> {
            val pass = f["password"].orEmpty().trim()
            if (pass.isEmpty() || addr.isEmpty() || port.isEmpty()) return null
            // Userinfo is percent-encoded (the parser decodes it).
            "trojan://${enc(pass)}@$addr:$port" + query(
                "security" to f["security"].orEmpty().ifEmpty { "tls" },
                "sni" to f["sni"].orEmpty(),
                // 3.2.7 (PattNG share link). 3.2.9 (the rest).
                "fp" to f["fp"].orEmpty(),
                "allowInsecure" to if (f["allowInsecure"] == "1") "1" else "",
                "alpn" to f["alpn"].orEmpty(),
                "cs" to f["cs"].orEmpty(),
                "pbk" to f["pbk"].orEmpty(),
                "sid" to f["sid"].orEmpty(),
                "ech" to f["ech"].orEmpty(),
                "echOutbound" to f["echOutbound"].orEmpty(),
                "vcn" to f["vcn"].orEmpty(),
                "pcs" to f["pcs"].orEmpty(),
                "spx" to f["spx"].orEmpty(),
                "pqv" to f["pqv"].orEmpty(),
            ) + frag
        }
        "ss" -> {
            val pass = f["password"].orEmpty()
            val method = f["method"].orEmpty().ifEmpty { "aes-256-gcm" }
            if (pass.isEmpty() || addr.isEmpty() || port.isEmpty()) return null
            val raw = "$method:$pass@$addr:$port"
            "ss://" + Base64.encodeToString(
                raw.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            ) + frag
        }
        "socks", "http" -> {
            val user = f["username"].orEmpty().trim()
            val pass = f["password"].orEmpty()
            if (addr.isEmpty() || port.isEmpty()) return null
            val auth = if (user.isEmpty()) "" else "${enc(user)}:${enc(pass)}@"
            "$proto://$auth$addr:$port$frag"
        }
        "hysteria2" -> {
            val pass = f["password"].orEmpty().trim()
            if (pass.isEmpty() || addr.isEmpty() || port.isEmpty()) return null
            "hysteria2://${enc(pass)}@$addr:$port" + query(
                "sni" to f["sni"].orEmpty(),
                "insecure" to f["insecure"].orEmpty(),
                // 1.0.2 (PattNG Hysteria2Fmt): obfs=salamander is
                // written only when an obfs password is set; query()
                // drops the empty pairs either way.
                "obfs" to if (f["obfsPassword"].orEmpty().isEmpty()) "" else "salamander",
                "obfs-password" to f["obfsPassword"].orEmpty(),
                // 3.2.9 (PattNG): ECH + pinning ride on Hysteria2 too.
                "ech" to f["ech"].orEmpty(),
                "echOutbound" to f["echOutbound"].orEmpty(),
                "vcn" to f["vcn"].orEmpty(),
                "pcs" to f["pcs"].orEmpty(),
            ) + frag
        }
        "wg" -> {
            val priv = f["privateKey"].orEmpty().trim()
            val pub = f["publicKey"].orEmpty().trim()
            if (priv.isEmpty() || pub.isEmpty() || addr.isEmpty() || port.isEmpty()) return null
            "wg://${enc(priv)}@$addr:$port" + query(
                "publicKey" to pub,
                "allowedIPs" to f["allowedIps"].orEmpty().ifEmpty { "0.0.0.0/0,::/0" },
                "address" to f["localAddress"].orEmpty(),
                "mtu" to f["mtu"].orEmpty(),
            ) + frag
        }
        "json" -> {
            val json = f["json"].orEmpty().trim()
            if (!json.startsWith("{")) return null
            "custom://" + Base64.encodeToString(
                json.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            ) + frag
        }
        else -> null
    }
}
