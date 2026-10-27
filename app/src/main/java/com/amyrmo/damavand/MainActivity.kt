package com.amyrmo.damavand

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.amyrmo.damavand.core.AndroidVpn
import com.amyrmo.damavand.core.StartPlanner
import com.amyrmo.damavand.core.VaultImportBus
import com.amyrmo.damavand.core.VpnManager
import com.amyrmo.damavand.core.platform.LocaleManager
import com.amyrmo.damavand.data.config.ConfigRepository
import com.amyrmo.damavand.data.settings.SettingsRepository
import com.amyrmo.damavand.domain.model.ThemeMode
import com.amyrmo.damavand.platform.AndroidPlatform
import com.amyrmo.damavand.ui.MainScaffold
import com.amyrmo.damavand.ui.platform.FilePick
import com.amyrmo.damavand.ui.platform.LocalPlatform
import com.amyrmo.damavand.ui.theme.DamavandTheme
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private val configRepository: ConfigRepository
        get() = (application as DamavandApp).container.configRepository

    private val settingsRepository: SettingsRepository
        get() = (application as DamavandApp).container.settingsRepository

    private var pendingStart: StartPlanner.Plan.Start? = null

    private val planner by lazy { StartPlanner(this) }

    private var planning = false

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val start = pendingStart
            pendingStart = null
            if (result.resultCode == RESULT_OK && start != null) {
                AndroidVpn.start(this, start.configJson, start.remark, start.socksPort, start.kind)
            } else if (result.resultCode != RESULT_OK) {
                toast(getString(R.string.vpn_permission_denied))
            }
        }

    private var pendingIkev2Remark: String? = null
    private val ikev2ConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val remark = pendingIkev2Remark
            pendingIkev2Remark = null
            if (result.resultCode == RESULT_OK && remark != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    com.amyrmo.damavand.core.Ikev2Controller.startProvisioned(this@MainActivity, remark)
                }
            } else {
                com.amyrmo.damavand.core.Ikev2Controller.forgetProvisionedProfile(this)
                VpnManager.onDisconnected()
                if (result.resultCode != RESULT_OK) toast(getString(R.string.vpn_permission_denied))
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val openZsxLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            offerLockedConfig(uri)
        }

    private fun importZsx() {
        openZsxLauncher.launch(arrayOf("*/*"))
    }

    private var filePickPending: CompletableDeferred<FilePick?>? = null
    private val filePickLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val pending = filePickPending
            filePickPending = null
            if (uri == null) { pending?.complete(null); return@registerForActivityResult }
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            pending?.complete(if (bytes != null) FilePick(name, bytes) else null)
        }

    private var qrScanPending: ((String?) -> Unit)? = null
    private val qrScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = qrScanPending
            qrScanPending = null
            pending?.invoke(
                result.data?.getStringExtra(com.amyrmo.damavand.qr.QrScanActivity.EXTRA_RESULT)
                    ?.takeIf { result.resultCode == RESULT_OK },
            )
        }

    private var imagePickPending: CompletableDeferred<ByteArray?>? = null
    private val imagePickLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val pending = imagePickPending
            imagePickPending = null
            pending?.complete(
                uri?.let {
                    runCatching { contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }
                        .getOrNull()
                },
            )
        }

    private suspend fun pickImage(): ByteArray? {
        imagePickPending?.complete(null)
        val deferred = CompletableDeferred<ByteArray?>()
        imagePickPending = deferred
        imagePickLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
        return deferred.await()
    }

    private fun scanQr(onResult: (String?) -> Unit) {
        qrScanPending?.invoke(null)
        qrScanPending = onResult
        qrScanLauncher.launch(Intent(this, com.amyrmo.damavand.qr.QrScanActivity::class.java))
    }

    private suspend fun pickFile(): FilePick? {
        filePickPending?.complete(null)
        val deferred = CompletableDeferred<FilePick?>()
        filePickPending = deferred
        filePickLauncher.launch(arrayOf("*/*"))
        return deferred.await()
    }

    private var certInstallPending: CompletableDeferred<com.amyrmo.damavand.ui.servers.CertInstallResult>? = null
    private val certInstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = certInstallPending
            certInstallPending = null
            pending?.complete(
                if (result.resultCode == RESULT_OK) {
                    com.amyrmo.damavand.ui.servers.CertInstallResult.Installed
                } else {
                    com.amyrmo.damavand.ui.servers.CertInstallResult.Cancelled
                }
            )
        }

    private suspend fun installCredential(
        bytes: ByteArray,
        suggestedName: String,
    ): com.amyrmo.damavand.ui.servers.CertInstallResult {
        certInstallPending?.complete(com.amyrmo.damavand.ui.servers.CertInstallResult.Cancelled)
        val deferred = CompletableDeferred<com.amyrmo.damavand.ui.servers.CertInstallResult>()
        certInstallPending = deferred
        val intent = android.security.KeyChain.createInstallIntent().apply {
            putExtra(android.security.KeyChain.EXTRA_PKCS12, bytes)
            if (suggestedName.isNotBlank()) {
                putExtra(android.security.KeyChain.EXTRA_NAME, suggestedName)
            }
        }
        return runCatching { certInstallLauncher.launch(intent); deferred.await() }
            .getOrElse {
                certInstallPending = null
                com.amyrmo.damavand.ui.servers.CertInstallResult.Unsupported
            }
    }

    private val androidPlatform by lazy { AndroidPlatform(this, ::pickFile, ::scanQr, ::pickImage) }

    override fun attachBaseContext(newBase: Context) {
        val tag = SettingsRepository.readLanguageTag(newBase)
        super.attachBaseContext(LocaleManager.wrap(newBase, tag))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.reassert(SettingsRepository.readLanguageTag(this))
    }

    override fun onResume() {
        super.onResume()
        runCatching { com.amyrmo.damavand.core.Ikev2Controller.resync(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleDeepLink(intent)) handleIncomingZsx(intent)
        handleConnectRequest(intent)
    }

    private fun handleDeepLink(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        val scheme = data.scheme?.lowercase() ?: return false
        if (scheme !in com.amyrmo.damavand.domain.config.DeepLinkParser.SCHEMES) return false
        val request = com.amyrmo.damavand.domain.config.DeepLinkParser.parse(intent.dataString.orEmpty())
        if (request == null) {
            toast(getString(R.string.config_invalid))
        } else {
            com.amyrmo.damavand.core.DeepLinkBus.request(request)
        }
        return true
    }

    private fun handleConnectRequest(intent: Intent?) {
        if (intent?.action != com.amyrmo.damavand.core.VpnNotifications.ACTION_CONNECT) return

        intent.action = null
        if (VpnManager.status.value.state.isActive) return
        toggleConnection()
    }

    private fun handleIncomingZsx(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java,
            )
            else -> null
        } ?: return

        offerLockedConfig(uri)
    }

    private fun offerLockedConfig(uri: Uri) {
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            toast(getString(R.string.zsx_invalid))
            return
        }
        val peeked = runCatching { configRepository.peekLocked(bytes) }
        val meta = peeked.getOrNull()
        if (meta == null) {
            val legacy = peeked.exceptionOrNull() is com.amyrmo.damavand.crypto.ZsxLegacyException
            toast(getString(if (legacy) R.string.zsx_legacy else R.string.zsx_invalid))
            return
        }
        if (meta.isExpired) {
            toast(getString(R.string.zsx_expired))
            return
        }
        VaultImportBus.request(bytes, meta)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.amyrmo.damavand.core.Ikev2CertBridgeAndroid.install(this)
        com.amyrmo.damavand.core.CertImportAndroid.installer = ::installCredential
        com.amyrmo.damavand.core.CertImportAndroid.install()
        maybeRequestNotificationPermission()

        if (savedInstanceState == null) {
            if (!handleDeepLink(intent)) handleIncomingZsx(intent)
        }
        handleConnectRequest(intent)
        val repo = (application as DamavandApp).container.settingsRepository
        enableEdgeToEdge()
        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val langTag = settings.language.tag ?: resources.configuration.locales[0].language
            DamavandTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                accentColor = settings.accentColor,
                amoledBlack = settings.amoledBlack,
                colorStyle = settings.colorStyle,
                colorContrast = settings.colorContrast,
                design = settings.appDesign,
                animateColors = !settings.reduceMotion,
                languageTag = langTag,
                fontScale = settings.uiFontScale.scale,
            ) {
                CompositionLocalProvider(LocalPlatform provides androidPlatform) {
                    val aiBridge = androidx.compose.runtime.remember {
                        com.amyrmo.damavand.ai.AndroidAiBridge.create(
                            repository = configRepository,
                            settings = repo,
                            toggleConnection = ::toggleConnection,
                            mtuHint = {
                                val active = configRepository.activeProfile()
                                val link = (active?.source as? com.amyrmo.damavand.domain.config.ProfileSource.Link)?.link
                                val parsed = link?.let {
                                    runCatching { com.amyrmo.damavand.domain.config.ConfigParser.parse(it) }.getOrNull()
                                }
                                val host = parsed?.address?.takeIf { it.isNotBlank() }
                                    ?: active?.address.orEmpty()
                                if (host.isBlank()) {
                                    null
                                } else {
                                    com.amyrmo.damavand.data.net.MtuServerHint(
                                        host = host,
                                        overhead = parsed
                                            ?.let { com.amyrmo.damavand.data.net.MtuOverheads.ofServer(it) }
                                            ?: com.amyrmo.damavand.data.net.MtuOverheads.worstCase(),
                                    )
                                }
                            },
                        )
                    }
                    MainScaffold(
                        settings = settings,
                        configRepository = configRepository,
                        onToggleConnection = ::toggleConnection,
                        onActiveServerChanged = ::switchActiveServer,
                        onImportZsx = ::importZsx,
                        onUpdateSettings = repo::update,
                        onLanguage = { lang ->
                            repo.update { it.copy(language = lang) }
                            recreate()
                        },
                        aiBridge = aiBridge,
                        appVersion = com.amyrmo.damavand.BuildConfig.VERSION_NAME,
                    )

                    com.amyrmo.damavand.ui.OpenConnectAuthDialog()
                    com.amyrmo.damavand.ui.OpenConnectCertTrustDialog(
                        onRemember = { hash ->
                            configRepository.activeProfile()?.let {
                                configRepository.rememberOpenConnectCert(it.id, hash)
                            }
                        },
                    )
                    com.amyrmo.damavand.ui.OpenConnectSsoDialog()
                }
            }
        }
    }

    private val switchStopTimeoutMs = 8_000L

    private val switchSettleMs = 350L

    private fun switchActiveServer() {
        if (!VpnManager.status.value.state.isActive) return
        lifecycleScope.launch {
            toggleConnection()
            val stopped = withTimeoutOrNull(switchStopTimeoutMs) {
                VpnManager.status.first { !it.state.isActive && !it.state.isTransitioning }
                true
            } != null
            if (!stopped) {
                toast(getString(R.string.switch_server_timeout))
                return@launch
            }

            delay(switchSettleMs)
            toggleConnection()
        }
    }

    private fun toggleConnection() {
        val state = VpnManager.status.value.state
        if (state.isActive || state.isTransitioning) {
            if (com.amyrmo.damavand.core.Ikev2Controller.isActive) {
                com.amyrmo.damavand.core.Ikev2Controller.stop(this)
                return
            }
            AndroidVpn.stop(this)
            return
        }
        if (planning) return
        val profile = configRepository.activeProfile()
        if (profile == null) {
            toast(getString(R.string.select_config_first))
            return
        }
        planning = true
        lifecycleScope.launch {
            val plan = try {
                withContext(Dispatchers.IO) { planner.plan(profile) }
            } finally {
                planning = false
            }
            when (plan) {
                is StartPlanner.Plan.Ikev2 -> startIkev2(plan.remark, plan.profile)
                is StartPlanner.Plan.Failure -> toast(plan.message)
                is StartPlanner.Plan.Start -> {
                    if (plan.proxyOnly) {
                        AndroidVpn.start(this@MainActivity, plan.configJson, plan.remark, plan.socksPort, plan.kind, proxyOnly = true)
                        return@launch
                    }
                    val consent = VpnService.prepare(this@MainActivity)
                    if (consent != null) {
                        pendingStart = plan
                        vpnPermissionLauncher.launch(consent)
                    } else {
                        AndroidVpn.start(this@MainActivity, plan.configJson, plan.remark, plan.socksPort, plan.kind)
                    }
                }
            }
        }
    }

    private fun startIkev2(remark: String, ikev2: com.amyrmo.damavand.domain.config.Ikev2Profile) {
        val controller = com.amyrmo.damavand.core.Ikev2Controller
        if (!controller.isSupportedSdk) {
            toast(getString(R.string.ikev2_requires_android11))
            return
        }
        if (!controller.isSupported(this)) {
            toast(getString(R.string.ikev2_requires_ipsec))
            return
        }

        if (VpnManager.status.value.state.isActive) AndroidVpn.stop(this)
        VpnManager.onStarting(remark)

        controller.note("connect requested for $remark (server=${ikev2.server}, auth=${ikev2.effectiveAuth})")
        lifecycleScope.launch(Dispatchers.IO) {
            val consent = try {
                controller.provision(this@MainActivity, ikev2)
            } catch (e: com.amyrmo.damavand.core.Ikev2Exception) {
                controller.note("provision refused", e.cause ?: e)
                withContext(Dispatchers.Main) { VpnManager.onError(getString(e.messageRes)) }
                return@launch
            } catch (e: Exception) {
                controller.note("provision threw", e)
                withContext(Dispatchers.Main) {
                    VpnManager.onError(e.message ?: getString(R.string.ikev2_failed_generic))
                }
                return@launch
            }
            if (consent != null) {
                withContext(Dispatchers.Main) {
                    pendingIkev2Remark = remark
                    ikev2ConsentLauncher.launch(consent)
                }
            } else {
                controller.startProvisioned(this@MainActivity, remark)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
