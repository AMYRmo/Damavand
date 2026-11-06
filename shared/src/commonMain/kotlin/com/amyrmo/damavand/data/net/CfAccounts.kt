package com.amyrmo.damavand.data.net

import com.amyrmo.damavand.domain.config.Protocol
import com.amyrmo.damavand.domain.config.SecurityConfig
import com.amyrmo.damavand.domain.config.ServerConfig
import com.amyrmo.damavand.domain.config.TransportConfig
import com.amyrmo.damavand.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One deployment of the BPB worker in an account: a Worker script or a Pages
 * project (BPB's `Deployment` in cf_account.dart).
 *
 * [wsPath] is a Damavand addition: BPB generates a fresh random WS path on
 * every config build, but Damavand resolves the built-in "bpb" profile from
 * stored state at connect time, so the path is generated at deploy time,
 * persisted here, and regenerated on every redeploy — the faithful
 * equivalent of BPB's per-build freshness within a stored-state design.
 */
@Serializable
data class CfDeployment(
    val type: String,
    val name: String,
    val host: String,
    val customDomain: String? = null,
    val version: Int = 0,
    val pending: Boolean = false,
    val wsPath: String = "",
) {
    /** The host configs dial: the custom domain when one is attached. */
    val effectiveHost: String get() = customDomain ?: host

    fun ref(accountId: String) = CfDeploymentRef(accountId, type, name, host, customDomain)
}

/**
 * A Cloudflare account saved on this device, with the deployments this
 * install made in it (BPB's `SavedCfAccount`). SECRET: [token] and [uuid] —
 * they live only in this store (the app's private key-value store, the same
 * sanctioned pattern as the profile store, which holds private keys) and are
 * never exported: the settings backup encodes only AppSettings, which this
 * state is deliberately not part of.
 *
 * The VLESS knobs BPB keeps per deployment ([CfDeployment] in BPB carries a
 * sparse VlessSettings diff) are kept per account here: [proxyIps] and
 * [fallbackUrl] are server-side (they live in the worker's ENV binding;
 * editing them marks deployments pending), [cleanIps] and [ports] are
 * client-side and apply at the next connect via live resolution.
 */
@Serializable
data class CfAccount(
    val id: String,
    val name: String,
    val token: String,
    val email: String = "",
    val uuid: String,
    val deployments: List<CfDeployment> = emptyList(),
    val proxyIps: List<String> = emptyList(),
    val fallbackUrl: String = "",
    val cleanIps: List<String> = DEFAULT_CLEAN_IPS,
    val ports: List<Int> = listOf(443),
) {
    /** Email when known, otherwise the account name (BPB's label rule). */
    val label: String get() = email.ifEmpty { name }

    fun deployment(type: String): CfDeployment? = deployments.firstOrNull { it.type == type }

    /** Both Workers and Pages are deployed (BPB's completeness rule). */
    val isComplete: Boolean
        get() = deployment(CloudflareApi.TYPE_WORKERS) != null && deployment(CloudflareApi.TYPE_PAGES) != null

    /** A deployment predates the bundled worker version (BPB's Update badge rule). */
    val updateAvailable: Boolean
        get() = deployments.any { it.version < CloudflareApi.WORKER_VERSION }

    fun withDeployment(d: CfDeployment): CfAccount = copy(
        deployments = (deployments.filterNot { it.type == d.type } + d)
            .sortedBy { if (it.type == CloudflareApi.TYPE_WORKERS) 0 else 1 },
    )

    fun env(): String = CloudflareApi.encodeEnv(uuid, proxyIps, fallbackUrl)

    companion object {
        /** BPB's default VLESS clean IPs (settings.dart). */
        val DEFAULT_CLEAN_IPS = listOf("104.17.148.22", "104.17.147.22")
    }
}

@Serializable
data class CfState(
    val accounts: List<CfAccount> = emptyList(),
    val selectedAccountId: String? = null,
    /** Which deployment the built-in "bpb" profile resolves to (BPB's Connection switch). */
    val connectionType: String = CloudflareApi.TYPE_WORKERS,
) {
    fun selected(): CfAccount? =
        accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
}

/**
 * Owns the Cloudflare account state for the BPB tab: token validation and
 * account connect (phase 1), deployments (phase 2), live profile resolution
 * (phase 4) and account management (phase 5).
 *
 * Storage: its own KeyValueStore file ("zed_cf" on Android, created in
 * AppContainer) — app-private SharedPreferences/files, exactly where
 * ConfigRepository keeps profiles and their private keys. Nothing here is
 * reachable from SettingsBackup, share links, or logs.
 */
class CfAccountsRepository(private val store: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<CfState> = _state.asStateFlow()

    fun selectedAccount(): CfAccount? = _state.value.selected()

    private fun load(): CfState {
        val raw = store.getString(KEY_STATE) ?: return CfState()
        return runCatching { json.decodeFromString<CfState>(raw) }.getOrDefault(CfState())
    }

    private fun set(next: CfState) {
        _state.value = next
        store.putString(KEY_STATE, json.encodeToString(next))
    }

    private fun updateAccount(id: String, transform: (CfAccount) -> CfAccount) {
        val current = _state.value
        set(current.copy(accounts = current.accounts.map { if (it.id == id) transform(it) else it }))
    }

    /**
     * BPB's token validation: normalize the pasted text, then GET /accounts
     * must return at least one account. Returns the normalized token and the
     * accounts it can reach (the UI offers a picker when there are several,
     * like BPB's wizard).
     */
    suspend fun validateToken(raw: String, socksPort: Int? = null): Pair<String, List<CfAccountInfo>> {
        val token = CloudflareApi.normalizeToken(raw)
        val api = CloudflareApi(token).also { it.socksPort = socksPort }
        val accounts = api.accounts()
        if (accounts.isEmpty()) throw CfException("This token has no account access")
        return token to accounts
    }

    /**
     * Saves [info] as a connected account with a fresh UUID (one per account,
     * generated at connect — BPB parity) and selects it. Re-adding an account
     * that is already connected first deletes its earlier deployments from
     * Cloudflare, best effort (BPB's addCfAccount), then replaces the local
     * record.
     */
    suspend fun addAccount(token: String, info: CfAccountInfo, socksPort: Int? = null): CfAccount {
        _state.value.accounts.firstOrNull { it.id == info.id }?.let { old ->
            if (old.deployments.isNotEmpty()) {
                val oldApi = apiFor(old, socksPort)
                old.deployments.forEach { d ->
                    runCatching { oldApi.deleteDeployment(d.ref(old.id)) }
                }
            }
        }
        val email = CloudflareApi(token).also { it.socksPort = socksPort }.userEmail()
        val account = CfAccount(
            id = info.id,
            name = info.name,
            token = token,
            email = email,
            uuid = CloudflareApi.generateUuid(),
        )
        val current = _state.value
        set(
            current.copy(
                accounts = current.accounts.filterNot { it.id == info.id } + account,
                selectedAccountId = info.id,
            ),
        )
        return account
    }

    fun selectAccount(id: String) {
        set(_state.value.copy(selectedAccountId = id))
    }

    private fun apiFor(account: CfAccount, socksPort: Int?): CloudflareApi =
        CloudflareApi(account.token).also { it.socksPort = socksPort }

    /**
     * Deploys (or redeploys) one side — Worker or Pages — of [accountId] and
     * persists the result. BPB deploys both in parallel and saves the account
     * only when both succeed; Damavand keeps a half that succeeded and lets
     * the other side be retried (the kinder sequencing the audit suggested).
     * A redeploy reuses the deployment's name/host, refreshes its WS path
     * (BPB regenerates the path per build) and records the bundled script
     * version. Returns the deployment host.
     */
    suspend fun deployOne(accountId: String, type: String, socksPort: Int? = null): String {
        val account = _state.value.accounts.firstOrNull { it.id == accountId }
            ?: throw CfException("Account not found")
        val api = apiFor(account, socksPort)
        val script = CloudflareApi.loadWorkerScript()
        val env = account.env()
        val existing = account.deployment(type)
        val name = existing?.name ?: api.freeName(accountId, type)
        val host = if (type == CloudflareApi.TYPE_PAGES) {
            api.deployPages(accountId, name, env, create = existing == null, script = script)
                .ifBlank { "$name.pages.dev" }
        } else {
            api.deployWorker(accountId, name, env, script)
        }
        updateAccount(accountId) { acc ->
            acc.withDeployment(
                CfDeployment(
                    type = type,
                    name = name,
                    host = host,
                    customDomain = existing?.customDomain,
                    version = CloudflareApi.WORKER_VERSION,
                    pending = false,
                    wsPath = CloudflareApi.newWsPath(),
                ),
            )
        }
        return host
    }

    /** Deploys both sides; each side's outcome is reported separately. */
    suspend fun deployBoth(
        accountId: String,
        socksPort: Int? = null,
    ): Map<String, Result<String>> = mapOf(
        CloudflareApi.TYPE_WORKERS to runCatching { deployOne(accountId, CloudflareApi.TYPE_WORKERS, socksPort) },
        CloudflareApi.TYPE_PAGES to runCatching { deployOne(accountId, CloudflareApi.TYPE_PAGES, socksPort) },
    )

    fun setConnectionType(type: String) {
        set(_state.value.copy(connectionType = type))
    }

    /**
     * Saves the VLESS knobs for [accountId]. [proxyIps] and [fallbackUrl] are
     * server-side (they live in the worker's ENV): when they change, both
     * deployments are marked pending — BPB's semantics — and the section
     * offers a redeploy that re-encodes ENV and clears the flag. [cleanIps]
     * and [ports] are client-side and apply at the next connect.
     */
    fun updateKnobs(
        accountId: String,
        proxyIps: List<String>,
        fallbackUrl: String,
        cleanIps: List<String>,
        ports: List<Int>,
    ) {
        updateAccount(accountId) { acc ->
            val envChanged = acc.proxyIps != proxyIps || acc.fallbackUrl != fallbackUrl
            acc.copy(
                proxyIps = proxyIps,
                fallbackUrl = fallbackUrl,
                cleanIps = cleanIps,
                ports = ports,
                deployments = if (envChanged) {
                    acc.deployments.map { it.copy(pending = true) }
                } else {
                    acc.deployments
                },
            )
        }
    }

    /**
     * Live resolution behind the built-in "bpb" profile: the selected
     * account's deployment of the type chosen by the BPB tab's Connection
     * switch (Worker by default), as a [ServerConfig] — or null when no
     * account is connected or that deployment does not exist yet. Read fresh
     * on every connect, so redeploys and knob edits apply with no profile
     * ever being rewritten.
     */
    fun resolvedServer(): ServerConfig? {
        val state = _state.value
        val account = state.selected() ?: return null
        val deployment = account.deployment(state.connectionType) ?: return null
        if (deployment.host.isBlank() || deployment.wsPath.isBlank()) return null
        return bpbServerConfig(account, deployment)
    }

    /**
     * Attaches [hostname] as the custom domain of the account's [type]
     * deployment (BPB's flow: Workers domains API, or Pages domain + proxied
     * CNAME). Throws with Cloudflare's own message when the hostname's zone
     * is not on this account — surfaced honestly in the UI.
     */
    suspend fun attachDomain(accountId: String, type: String, hostname: String, socksPort: Int? = null) {
        val account = _state.value.accounts.firstOrNull { it.id == accountId }
            ?: throw CfException("Account not found")
        val deployment = account.deployment(type) ?: throw CfException("Not deployed yet")
        val host = hostname.trim().lowercase()
        apiFor(account, socksPort).attachCustomDomain(deployment.ref(accountId), host)
        updateAccount(accountId) { acc ->
            acc.deployment(type)?.let { acc.withDeployment(it.copy(customDomain = host)) } ?: acc
        }
    }

    /** Detaches the custom domain of the account's [type] deployment. */
    suspend fun detachDomain(accountId: String, type: String, socksPort: Int? = null) {
        val account = _state.value.accounts.firstOrNull { it.id == accountId }
            ?: throw CfException("Account not found")
        val deployment = account.deployment(type) ?: throw CfException("Not deployed yet")
        val domain = deployment.customDomain ?: return
        apiFor(account, socksPort).detachCustomDomain(deployment.ref(accountId), domain)
        updateAccount(accountId) { acc ->
            acc.deployment(type)?.let { acc.withDeployment(it.copy(customDomain = null)) } ?: acc
        }
    }

    /**
     * Request usage for the selected deployment (GraphQL; BPB's home card):
     * this deployment's requests and the account total over the last 24 h.
     */
    suspend fun usage(accountId: String, socksPort: Int? = null): CfUsage {
        val account = _state.value.accounts.firstOrNull { it.id == accountId }
            ?: throw CfException("Account not found")
        val deployment = account.deployment(_state.value.connectionType)
            ?: account.deployments.firstOrNull()
            ?: throw CfException("Nothing deployed yet")
        return apiFor(account, socksPort).workerUsage(accountId, deployment.name)
    }

    /**
     * BPB's remove: delete the account's custom domains and both deployments
     * from Cloudflare, then drop the local record. Throws when the Cloudflare
     * cleanup fails — the UI then offers BPB's "remove anyway" fallback
     * ([disconnect]).
     */
    suspend fun removeAccount(id: String, socksPort: Int? = null) {
        val account = _state.value.accounts.firstOrNull { it.id == id } ?: return
        val api = apiFor(account, socksPort)
        for (d in account.deployments) {
            api.deleteDeployment(d.ref(id))
        }
        disconnect(id)
    }

    /**
     * Basic disconnect: forget the token and the local record. The
     * Cloudflare-side deployments are left in place (the UI copy says so);
     * also the remove-anyway fallback of [removeAccount].
     */
    fun disconnect(id: String) {
        val current = _state.value
        val accounts = current.accounts.filterNot { it.id == id }
        set(
            current.copy(
                accounts = accounts,
                selectedAccountId = if (current.selectedAccountId == id) {
                    accounts.firstOrNull()?.id
                } else {
                    current.selectedAccountId
                },
            ),
        )
    }

    private companion object {
        const val KEY_STATE = "cf_state_v1"
    }
}

/**
 * BPB's config model for one deployment, as a Damavand [ServerConfig]:
 * VLESS / WS / TLS to the deployment's domain (its custom domain when one is
 * attached), the account UUID, the deployment's persisted WS path, SNI/Host =
 * the domain, BPB's default uTLS fingerprint ("chrome"), and candidate
 * endpoints = clean IPs ∪ domain over the chosen ports — which the Xray
 * builder's existing best-ping machinery (Tier 2) turns into an autoselect
 * balancer group, exactly BPB's Best-Ping shape mapped onto Damavand's.
 * BPB randomizes the SNI's letter casing per build; Damavand's builder owns
 * SNI handling, so the domain is passed as-is.
 */
fun bpbServerConfig(account: CfAccount, deployment: CfDeployment): ServerConfig {
    val domain = deployment.effectiveHost
    val ports = account.ports.ifEmpty { listOf(443) }
    return ServerConfig(
        protocol = Protocol.VLESS,
        remark = "bpb",
        address = domain,
        port = ports.first(),
        userId = account.uuid,
        encryption = "none",
        transport = TransportConfig(
            network = "ws",
            host = domain,
            path = deployment.wsPath,
        ),
        tls = SecurityConfig(
            security = "tls",
            sni = domain,
            fingerprint = "chrome",
        ),
        candidateAddresses = buildList {
            account.cleanIps.forEach { ip -> ports.forEach { p -> add("$ip:$p") } }
            ports.forEach { p -> add("$domain:$p") }
        }.distinct(),
    )
}
