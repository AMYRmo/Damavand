/*
 * Adapted from ZedSecure by CluvexStudio (AGPL-3.0); Damavand is the
 * owner's own fork lineage of that codebase.
 *
 * Deferred-surface port of ZedSecure's PixelDetails / IP-details page
 * (commit 9447b34) and its Cloudflare speedtest/ surface: the IP panel
 * shows only real [GeoInfo] data the session layer already fetched, and
 * the speed test is an honestly-labelled placeholder — no fake numbers,
 * no fake actions — until its backend wiring lands.
 */
package com.amyrmo.damavand.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.amyrmo.damavand.R
import com.amyrmo.damavand.platform.GeoInfo
import com.amyrmo.damavand.ui.components.GlassCard

/**
 * Real exit-IP details for the live session: every value comes from
 * [GeoInfo] (the lookup through the session's own SOCKS port, §5.5).
 * Fields the lookup did not return render as "…", never as invented data.
 *
 * The DNS-leak row is deliberately honest: v2 has no leak-check backend
 * yet, so the row says exactly that instead of faking a result.
 */
@Composable
fun IpDetailsPanel(
    geo: GeoInfo?,
    socksPort: Int?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.home_dashboard_title),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        IpDetailRow(
            label = stringResource(R.string.home_exit_ip),
            value = geo?.ip ?: "…",
            mono = true,
        )
        IpDetailRow(
            label = stringResource(R.string.home_location),
            value = listOfNotNull(geo?.city, geo?.country)
                .joinToString(", ")
                .ifBlank { "…" },
        )
        IpDetailRow(
            label = stringResource(R.string.common_ping),
            value = geo?.pingMs?.let { "$it ms" } ?: "…",
            mono = true,
        )
        if (socksPort != null) {
            IpDetailRow(
                // TODO string key: no local-SOCKS-port label exists in strings.xml yet.
                label = "Local SOCKS port",
                value = "127.0.0.1:$socksPort",
                mono = true,
            )
        }
        Spacer(Modifier.height(6.dp))
        IpDetailRow(
            // TODO string key: no DNS-leak label exists in strings.xml yet.
            label = "DNS leak check",
            // TODO string key: honestly deferred — there is no leak-check backend in v2 yet.
            value = "Not yet available",
        )
    }
}

@Composable
private fun IpDetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(6.dp))
}

/**
 * The speed-test surface, deferred: ZedSecure runs this against
 * Cloudflare's speed-test backend; v2 has no such wiring yet, so the card
 * states that plainly and its button is disabled rather than decorative.
 * No throughput numbers are shown — there is nothing real to show.
 */
@Composable
fun SpeedTestCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier = modifier) {
        Column {
            Text(
                // TODO string key: no speed-test title exists in strings.xml yet.
                text = "Speed test",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                // TODO string key: deferred-surface copy; the Cloudflare
                // speed test arrives with its backend wiring.
                text = "The Cloudflare speed test arrives with its backend wiring — not available yet.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = {}, enabled = false) {
                Text(stringResource(R.string.common_test))
            }
        }
    }
}
