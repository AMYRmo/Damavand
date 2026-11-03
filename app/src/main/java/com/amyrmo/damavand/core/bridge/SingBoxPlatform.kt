package com.amyrmo.damavand.core.bridge

import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import com.amyrmo.damavand.logic.PerAppSelection
import java.io.IOException
import java.net.InetSocketAddress
import libbox.BridgeOptions
import libbox.BridgeSession
import libbox.ConnectionOwner
import libbox.InterfaceUpdateListener
import libbox.Libbox
import libbox.LocalDNSTransport
import libbox.NeighborUpdateListener
import libbox.NetworkInterfaceIterator
import libbox.Notification
import libbox.PlatformInterface
import libbox.PlatformUser
import libbox.RoutePrefixIterator
import libbox.ShellSession
import libbox.StringIterator
import libbox.TunOptions
import libbox.WIFIState

/**
 * The libbox `PlatformInterface` implementation, extracted from v1's
 * `SingBoxEngine` (architecture §2.5: moved out of the engine class into
 * `:app/core/bridge/` for testability). Everything is faithful to v1
 * except the three seams the v2 shape changes:
 *
 *  (a) per-app routing comes from the [perApp] constructor parameter (the
 *      session's [PerAppSelection]) instead of v1's `AppSettings` fields.
 *      It merges with the config-provided lists from libbox's TunOptions
 *      exactly as v1 merged its two sources. Mapping (v1 semantics —
 *      allowed list non-empty ⇒ allowed mode; else disallowed mode with
 *      self always disallowed):
 *        - [PerAppSelection.Include] → allowed candidates = pkgs − self;
 *          if the merged allowed list ends up empty, fall through to
 *          disallowed mode (self only, plus config excludes);
 *        - [PerAppSelection.Exclude] → disallowed = pkgs + self;
 *        - [PerAppSelection.All] → disallowed = self only.
 *  (b) `sendNotification` forwards to the [postNotification] callback
 *      (title, body — falling back to the subtitle when the body is
 *      blank); v2 has no VpnNotifications channel/icon of its own here.
 *      `cancelNotification` still cancels through NotificationManager,
 *      as v1 did.
 *  (c) `openTun` reports the established descriptor through
 *      [onTunEstablished] after establishing it.
 *
 * The platform owns the TUN descriptor and the default-interface monitor:
 * [shutdown] (called by the engine's stop) tears both down, as v1's
 * engine stop did.
 */
class SingBoxPlatform(
    private val service: VpnService,
    private val sessionName: String,
    private val perApp: PerAppSelection,
    private val postNotification: (title: String, body: String) -> Unit,
    private val onTunEstablished: (ParcelFileDescriptor) -> Unit = {},
) : PlatformInterface {

    @Volatile
    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Engine stop hook: stop the interface monitor and close the TUN (v1 `stop()`). */
    fun shutdown() {
        stopInterfaceMonitor()
        runCatching { tun?.close() }
        tun = null
    }

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!service.protect(fd)) throw IOException("protect($fd) failed")
    }

    override fun openTun(options: TunOptions): Int = openTunInterface(options)

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw UnsupportedOperationException("finding a connection's app needs Android 10")
        }
        val cm = service.getSystemService(ConnectivityManager::class.java)
            ?: throw IOException("no connectivity service")
        val uid = cm.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort),
        )
        if (uid == Process.INVALID_UID) throw IOException("connection owner not found")
        val packages = service.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        return ConnectionOwner().apply {
            userId = uid
            userName = packages.firstOrNull().orEmpty()
            setAndroidPackageNames(strings(packages))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) =
        startInterfaceMonitor(listener)

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) = stopInterfaceMonitor()

    override fun getInterfaces(): NetworkInterfaceIterator = networkInterfaces()

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun clearDNSCache() = Unit

    override fun sendNotification(notification: Notification) {
        postNotification(notification.title, notification.body.ifBlank { notification.subtitle })
    }

    override fun cancelNotification(identifier: String, typeID: Int) {
        service.getSystemService(NotificationManager::class.java)?.cancel(identifier, typeID)
    }

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun registerMyInterface(name: String) = Unit

    override fun usePlatformShell(): Boolean = false
    override fun checkPlatformShell() = throw UnsupportedOperationException("no shell on this platform")
    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int,
    ): ShellSession = throw UnsupportedOperationException("no shell on this platform")

    override fun lookupUser(username: String?): PlatformUser = throw UnsupportedOperationException("no users")
    override fun lookupSFTPServer(): String = throw UnsupportedOperationException("no SFTP server")
    override fun readSystemSSHHostKey(): String = throw UnsupportedOperationException("no SSH host key")

    override fun tailscaleHostname(): String = Build.MODEL.orEmpty().ifBlank { "android" }

    override fun usePlatformBridge(): Boolean = false
    override fun createBridge(options: BridgeOptions?): BridgeSession =
        throw UnsupportedOperationException("bridges need root")

    private fun openTunInterface(options: TunOptions): Int {
        if (VpnService.prepare(service) != null) throw IOException("the VPN permission was revoked")
        val builder = service.Builder()
            .setSession(sessionName.ifBlank { "Damavand" })
            .setMtu(options.mtu)

        options.inet4Address.forEach { builder.addAddress(it.address(), it.prefix()) }
        val hasIpv6 = options.inet6Address.hasAny()
        options.inet6Address.forEach { builder.addAddress(it.address(), it.prefix()) }

        if (options.autoRoute) {
            runCatching { options.dnsServerAddress.forEach { builder.addDnsServer(it) } }

            var routed4 = false
            options.inet4RouteRange.forEach { builder.addRoute(it.address(), it.prefix()); routed4 = true }
            if (!routed4) builder.addRoute("0.0.0.0", 0)
            if (hasIpv6) {
                var routed6 = false
                options.inet6RouteRange.forEach { builder.addRoute(it.address(), it.prefix()); routed6 = true }
                if (!routed6) builder.addRoute("::", 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                options.inet4RouteExcludeAddress.forEach { excludeRoute(builder, it.address(), it.prefix()) }
                options.inet6RouteExcludeAddress.forEach { excludeRoute(builder, it.address(), it.prefix()) }
            }
        } else {
            options.inet4RouteAddress.forEach { builder.addRoute(it.address(), it.prefix()) }
            options.inet6RouteAddress.forEach { builder.addRoute(it.address(), it.prefix()) }
        }

        applyAppLists(builder, options.includePackage.toList(), options.excludePackage.toList())

        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bypass = options.httpProxyBypassDomain.toList()
            builder.setHttpProxy(ProxyInfo.buildDirectProxy(options.httpProxyServer, options.httpProxyServerPort, bypass))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val pfd = builder.establish() ?: throw IOException("the VPN interface could not be established")
        tun?.close()
        tun = pfd
        onTunEstablished(pfd)
        return pfd.fd
    }

    private fun excludeRoute(builder: VpnService.Builder, address: String, prefix: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            builder.excludeRoute(android.net.IpPrefix(java.net.InetAddress.getByName(address), prefix))
        }
    }

    private fun applyAppLists(builder: VpnService.Builder, include: List<String>, exclude: List<String>) {
        val self = service.packageName
        val settingsAllowed: Set<String> = when (val selection = perApp) {
            is PerAppSelection.Include -> selection.packages
            else -> emptySet()
        }
        val settingsDisallowed: Set<String> = when (val selection = perApp) {
            is PerAppSelection.Exclude -> selection.packages
            else -> emptySet()
        }
        val allowed = (include + settingsAllowed)
            .distinct()
            .filter { it != self }
        if (allowed.isNotEmpty()) {
            allowed.forEach { pkg -> runCatching { builder.addAllowedApplication(pkg) } }
            return
        }
        val disallowed = (exclude + settingsDisallowed + self).distinct()
        disallowed.forEach { pkg -> runCatching { builder.addDisallowedApplication(pkg) } }
    }

    private fun startInterfaceMonitor(listener: InterfaceUpdateListener) {
        val cm = service.getSystemService(ConnectivityManager::class.java) ?: return
        stopInterfaceMonitor()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = report(cm, network, listener)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                report(cm, network, listener)

            override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) =
                report(cm, network, listener)

            override fun onLost(network: Network) {
                listener.updateDefaultInterface("", -1, false, false)
            }
        }

        cm.registerDefaultNetworkCallback(callback)
        networkCallback = callback
        cm.activeNetwork?.let { report(cm, it, listener) }
    }

    private fun report(cm: ConnectivityManager, network: Network, listener: InterfaceUpdateListener) {
        val lp = cm.getLinkProperties(network) ?: return
        val name = lp.interfaceName ?: return
        val index = runCatching { java.net.NetworkInterface.getByName(name)?.index }.getOrNull() ?: return
        val caps = cm.getNetworkCapabilities(network)
        val expensive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        listener.updateDefaultInterface(name, index, expensive, false)
    }

    private fun stopInterfaceMonitor() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { service.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    @Suppress("DEPRECATION")
    private fun networkInterfaces(): NetworkInterfaceIterator {
        val cm = service.getSystemService(ConnectivityManager::class.java)
        val result = mutableListOf<libbox.NetworkInterface>()
        cm?.allNetworks?.forEach { network ->
            val lp = cm.getLinkProperties(network) ?: return@forEach
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@forEach
            val name = lp.interfaceName ?: return@forEach
            val ni = runCatching { java.net.NetworkInterface.getByName(name) }.getOrNull() ?: return@forEach
            result += libbox.NetworkInterface().apply {
                index = ni.index
                mtu = lp.mtu.takeIf { it > 0 } ?: runCatching { ni.mtu }.getOrDefault(1500)
                this.name = name
                addresses = strings(lp.linkAddresses.map { "${it.address.hostAddress?.substringBefore('%')}/${it.prefixLength}" })
                flags = linkFlags(ni)
                type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                dnsServer = strings(lp.dnsServers.mapNotNull { it.hostAddress })
                gateway = strings(lp.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway?.hostAddress })
                metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        val iterator = result.iterator()
        return object : NetworkInterfaceIterator {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): libbox.NetworkInterface = iterator.next()
        }
    }

    private fun linkFlags(ni: java.net.NetworkInterface): Int = runCatching {
        var flags = 0
        if (ni.isUp) flags = flags or IFF_UP or IFF_RUNNING
        if (ni.isLoopback) flags = flags or IFF_LOOPBACK
        if (ni.isPointToPoint) flags = flags or IFF_POINTOPOINT
        if (ni.supportsMulticast()) flags = flags or IFF_MULTICAST
        if (ni.interfaceAddresses.any { it.broadcast != null }) flags = flags or IFF_BROADCAST
        flags
    }.getOrDefault(0)

    companion object {
        private const val IFF_UP = 0x1
        private const val IFF_BROADCAST = 0x2
        private const val IFF_LOOPBACK = 0x8
        private const val IFF_POINTOPOINT = 0x10
        private const val IFF_RUNNING = 0x40
        private const val IFF_MULTICAST = 0x1000

        private fun strings(values: List<String>): StringIterator {
            val iterator = values.iterator()
            return object : StringIterator {
                override fun hasNext(): Boolean = iterator.hasNext()
                override fun len(): Int = values.size
                override fun next(): String = iterator.next()
            }
        }

        private fun StringIterator?.toList(): List<String> {
            val it = this ?: return emptyList()
            val out = mutableListOf<String>()
            while (it.hasNext()) it.next()?.let { v -> if (v.isNotBlank()) out += v }
            return out
        }

        private inline fun StringIterator?.forEach(action: (String) -> Unit) = toList().forEach(action)

        private inline fun RoutePrefixIterator?.forEach(action: (libbox.RoutePrefix) -> Unit) {
            val it = this ?: return
            while (it.hasNext()) it.next()?.let(action)
        }

        private fun RoutePrefixIterator?.hasAny(): Boolean = this?.hasNext() == true
    }
}
