package com.amyrmo.damavand.core.bridge

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.amyrmo.damavand.core.AppLog
import com.amyrmo.damavand.core.LogBus
import dev.amirzr.flutter_v2ray_client.v2ray.core.HevTunCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Forwards the TUN interface into a hop's (or the bridge's) loopback SOCKS
 * listener — v1 `DamavandVpnService.bridge()` extracted into the bridge
 * package (architecture §2.2: standalone Xray rows read "hev/zeptun
 * forwarder", zeptun preferred, hev fallback on the 6 s no-traffic proof,
 * as today).
 *
 * Behaviour, faithful to v1:
 *  - zeptun is tried first (Android Q+, as v1 gated it); if it refuses to
 *    start, hev starts instead;
 *  - if zeptun started, after [ZEPTUN_PROOF_MS] its counters are checked:
 *    a zeptun that carried nothing is stopped and hev takes over;
 *  - if hev then fails (or nothing started at all), [onFailure] reports it
 *    — v1 escalated to `VpnManager.onError` + full teardown; here the
 *    session layer decides, via the callback.
 *
 * Differences forced by the v2 shape (noted, not silent):
 *  - v1 read `hevIpv4`/`hevIpv6`/`useZepTun`/log level/rw-timeout from
 *    settings; the fixed v2 signature takes ipv4/ipv6 as parameters and
 *    has no settings toggles, so both cores run with their v1 defaults
 *    (log level "warn", rw timeout "300,60") and `pipeline = true`, which
 *    is what every v1 `bridge()` call site passed. A caller that needs
 *    zeptun disabled must not call this forwarder.
 *  - a generation counter guards the proof coroutine: a [stop] (or a
 *    later [start]) invalidates a still-pending proof from an earlier
 *    start, which v1's service-scoped coroutine could not distinguish.
 */
object TunForwarder {
    private const val TAG = "TunForwarder"

    /** v1 `ZEPTUN_PROOF_MS`: how long zeptun gets to prove it carries traffic. */
    private const val ZEPTUN_PROOF_MS = 6_000L

    @Volatile
    private var running = false

    @Volatile
    private var onZepTun = false

    @Volatile
    private var generation = 0

    /**
     * Start forwarding [tun] into the SOCKS listener on [socksPort].
     * Returns true when a forwarder (zeptun or hev) is running; false —
     * after invoking [onFailure] — when neither started.
     */
    fun start(
        service: VpnService,
        tun: ParcelFileDescriptor,
        socksPort: Int,
        mtu: Int,
        ipv4: String,
        ipv6: String?,
        scope: CoroutineScope,
        onFailure: (String) -> Unit,
    ): Boolean {
        val myGeneration = ++generation

        val startHev = {
            HevTunCore.start(
                context = service,
                tun = tun,
                socksPort = socksPort,
                mtu = mtu,
                ipv4 = ipv4,
                ipv6 = ipv6,
                preferIpv6 = ipv6 != null,
                pipeline = true,
            )
        }

        var zeptun = false
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            zeptun = ZepTunCore.start(
                service = service,
                tun = tun,
                socksPort = socksPort,
                mtu = mtu,
                ipv4 = ipv4,
                ipv6 = ipv6,
                pipeline = true,
            )
            if (zeptun) {
                LogBus.append("I/tun zeptun ${ZepTunCore.version} carrying the tunnel")
                true
            } else {
                AppLog.w(TAG, "zeptun did not start; falling back to hev")
                LogBus.append("W/tun zeptun did not start, falling back to hev")
                startHev()
            }
        } else {
            startHev()
        }

        onZepTun = zeptun
        running = ok

        if (ok && zeptun) {
            scope.launch {
                delay(ZEPTUN_PROOF_MS)
                if (generation != myGeneration || !running || !onZepTun) return@launch
                if (!ZepTunCore.carryingTraffic()) {
                    LogBus.append("W/tun zeptun carried nothing (${ZepTunCore.stats()}), switching to hev")
                    AppLog.w(TAG, "zeptun carried nothing (${ZepTunCore.stats()}); switching to hev")
                    ZepTunCore.stop()
                    onZepTun = false
                    if (!startHev()) {
                        running = false
                        onFailure("tun forwarder: zeptun carried no traffic and the hev fallback failed to start")
                    }
                }
            }
        }

        if (!ok) onFailure("tun forwarder failed to start (zeptun and hev both refused)")
        return ok
    }

    /** Stop whichever forwarder is running (v1 stopped both, unconditionally). */
    fun stop() {
        generation++
        running = false
        onZepTun = false
        ZepTunCore.stop()
        HevTunCore.stop()
    }
}
