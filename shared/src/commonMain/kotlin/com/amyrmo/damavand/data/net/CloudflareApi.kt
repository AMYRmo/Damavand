package com.amyrmo.damavand.data.net

import com.amyrmo.damavand.platform.currentTimeMillis
import com.amyrmo.damavand.platform.httpJson
import com.amyrmo.damavand.platform.httpPatchJson
import com.amyrmo.damavand.platform.secureRandomBytes
import com.amyrmo.damavand.platform.secureRandomToken
import com.amyrmo.damavand.shared.resources.Res
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A Cloudflare API failure, carrying the API's own message (BPB parity). */
class CfException(message: String) : Exception(message)

/** The built-in "bpb" profile was asked to connect before any deployment exists. */
class BpbNotReadyException : Exception("BPB deployment is not ready")

data class CfAccountInfo(val id: String, val name: String)

/** Workers requests over the last 24 h: the whole account and one script. */
data class CfUsage(val total: Long, val worker: Long)

/**
 * BPB-VPN's Cloudflare deployment suite, ported from
 * `lib/services/cloudflare_api.dart` (BPB-VPN, GPL-3.0). Minimal REST client
 * for deploying the bundled VLESS/WS worker — once as a Worker script, once
 * as a Pages project's `_worker.js` — with a single ENV binding. Every change
 * is a full deployment: the script with its one ENV binding.
 *
 * Transport is the app's platform httpJson layer (the one WarpMasque uses);
 * multipart bodies are assembled as text, exactly the parts BPB's Dart
 * client sends. The token is only ever held in memory by this client and in
 * the app's private key-value store by [CfAccountsRepository] — never logged.
 */
@OptIn(ExperimentalEncodingApi::class)
class CloudflareApi(rawToken: String) {

    val token: String = normalizeToken(rawToken)

    /**
     * When set, API calls go through this local SOCKS port — BPB routes its
     * calls through the live tunnel when one is up (the WarpMasque pattern:
     * the UI passes VpnManager's active port), which keeps the suite usable
     * where api.cloudflare.com is filtered. Null = direct.
     */
    var socksPort: Int? = null

    private val authHeaders: Map<String, String> get() = mapOf("Authorization" to "Bearer $token")

    // region REST plumbing

    private suspend fun send(
        method: String,
        path: String,
        jsonBody: JsonObject? = null,
        rawBody: String? = null,
        contentType: String? = null,
        timeoutMs: Int = 30_000,
    ): JsonElement? {
        val headers = buildMap {
            putAll(authHeaders)
            when {
                jsonBody != null -> put("Content-Type", "application/json")
                contentType != null -> put("Content-Type", contentType)
            }
        }
        val body = jsonBody?.toString() ?: rawBody
        val response = if (method == "PATCH") {
            httpPatchJson(BASE + path, headers, body.orEmpty(), readTimeoutMs = timeoutMs, socksPort = socksPort)
        } else {
            httpJson(method, BASE + path, headers, body, readTimeoutMs = timeoutMs, socksPort = socksPort)
        }
        return decode(response.code, response.body)
    }

    private suspend fun sendMultipart(path: String, parts: List<Part>): JsonElement? {
        val boundary = "----DamavandCf" + secureRandomToken(18)
        val body = buildString {
            parts.forEach { part ->
                append("--").append(boundary).append("\r\n")
                append("Content-Disposition: form-data; name=\"").append(part.name).append('"')
                part.filename?.let { append("; filename=\"").append(it).append('"') }
                append("\r\n")
                part.contentType?.let { append("Content-Type: ").append(it).append("\r\n") }
                append("\r\n")
                append(part.content)
                append("\r\n")
            }
            append("--").append(boundary).append("--\r\n")
        }
        return send(
            "POST",
            path,
            rawBody = body,
            contentType = "multipart/form-data; boundary=$boundary",
            timeoutMs = 90_000,
        )
    }

    /** BPB's _decode: success → result; 2xx with an empty/non-JSON body → null; else throw. */
    private fun decode(code: Int, body: String): JsonElement? {
        val data = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        if (data != null && data["success"]?.jsonPrimitive?.booleanOrNull == true) {
            return data["result"]
        }
        if (data == null && code in 200..299) return null
        val errors = (data?.get("errors") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::describeError) }
            .joinToString("; ")
        throw CfException(errors.ifEmpty { "Cloudflare API error $code" })
    }

    private fun describeError(e: JsonObject): String {
        val chain = (e["error_chain"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::describeError) }
            .joinToString(", ")
        val message = e["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val code = e["code"]?.jsonPrimitive?.contentOrNull
        return buildString {
            append(message)
            if (code != null) append(" (").append(code).append(")")
            if (chain.isNotEmpty()) append(": ").append(chain)
        }
    }

    private data class Part(
        val name: String,
        val content: String,
        val filename: String? = null,
        val contentType: String? = null,
    )

    // endregion

    // region account

    /** Email of the token's user (needs User Details: Read), or "" if unavailable. */
    suspend fun userEmail(): String = try {
        (send("GET", "/user") as? JsonObject)?.get("email")?.jsonPrimitive?.contentOrNull.orEmpty()
    } catch (e: CfException) {
        ""
    }

    suspend fun accounts(): List<CfAccountInfo> {
        val result = send("GET", "/accounts?per_page=50") as? JsonArray ?: return emptyList()
        return result.mapNotNull { element ->
            val a = element as? JsonObject ?: return@mapNotNull null
            CfAccountInfo(
                id = a["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = a["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    // endregion

    // region workers

    /** The account's workers.dev subdomain, registering a random one if it has none yet. */
    suspend fun ensureSubdomain(accountId: String): String {
        try {
            val result = send("GET", "/accounts/$accountId/workers/subdomain") as? JsonObject
            val sub = result?.get("subdomain")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (sub.isNotEmpty()) return sub
        } catch (e: CfException) {
            if (!e.message.orEmpty().contains("10007")) throw e // 10007: no subdomain registered
        }
        val result = send(
            "PUT",
            "/accounts/$accountId/workers/subdomain",
            jsonBody = buildJsonObject { put("subdomain", randomName(10)) },
        ) as? JsonObject
        return result?.get("subdomain")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    /** Uploads the worker script with its ENV binding (replacing every other binding). */
    suspend fun uploadScript(accountId: String, scriptName: String, env: String, script: String) {
        val metadata = buildJsonObject {
            put("main_module", "worker.js")
            put("compatibility_date", currentCompatibilityDate())
            put(
                "bindings",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "plain_text")
                            put("name", ENV_VAR)
                            put("text", env)
                        },
                    )
                },
            )
        }
        val boundary = "----DamavandCf" + secureRandomToken(18)
        val body = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"metadata\"\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(metadata.toString()).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"worker.js\"; filename=\"worker.js\"\r\n")
            append("Content-Type: application/javascript+module\r\n\r\n")
            append(script).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
        val response = httpJson(
            "PUT",
            "$BASE/accounts/$accountId/workers/scripts/$scriptName",
            authHeaders + ("Content-Type" to "multipart/form-data; boundary=$boundary"),
            body,
            readTimeoutMs = 90_000,
            socksPort = socksPort,
        )
        decode(response.code, response.body)
    }

    suspend fun enableWorkersDev(accountId: String, scriptName: String) {
        send(
            "POST",
            "/accounts/$accountId/workers/scripts/$scriptName/subdomain",
            jsonBody = buildJsonObject {
                put("enabled", true)
                put("previews_enabled", false)
            },
        )
    }

    /**
     * Routes [hostname] to the worker. The hostname's zone must be on the same
     * account. Only needs "Workers Scripts: Edit": the zone is passed by name,
     * trying the hostname's parent domains (longest first), so no zone-read
     * permission is needed.
     */
    suspend fun attachWorkerDomain(accountId: String, scriptName: String, hostname: String) {
        var lastError: CfException? = null
        for (zoneName in zoneCandidates(hostname)) {
            try {
                send(
                    "PUT",
                    "/accounts/$accountId/workers/domains",
                    jsonBody = buildJsonObject {
                        put("hostname", hostname)
                        put("service", scriptName)
                        put("zone_name", zoneName)
                    },
                )
                return
            } catch (e: CfException) {
                if (isAuthError(e)) throw e
                lastError = e
            }
        }
        throw CfException(
            "Could not attach $hostname: ${lastError?.message ?: "no matching zone"}. " +
                "The domain must be a zone on this Cloudflare account.",
        )
    }

    suspend fun detachWorkerDomain(accountId: String, scriptName: String, hostname: String) {
        val domains = send(
            "GET",
            "/accounts/$accountId/workers/domains?hostname=${urlEnc(hostname)}&service=${urlEnc(scriptName)}",
        ) as? JsonArray ?: return
        for (element in domains) {
            val d = element as? JsonObject ?: continue
            if (d["hostname"]?.jsonPrimitive?.contentOrNull != hostname) continue
            val id = d["id"]?.jsonPrimitive?.contentOrNull ?: continue
            send("DELETE", "/accounts/$accountId/workers/domains/$id")
        }
    }

    // endregion

    // region pages

    /** Direct-upload deployment of the worker as Pages' `_worker.js` (BPB's shape). */
    suspend fun uploadPagesDeployment(accountId: String, projectName: String, script: String) {
        sendMultipart(
            "/accounts/$accountId/pages/projects/$projectName/deployments",
            listOf(
                Part("manifest", "{}"),
                Part("branch", "main"),
                Part("_worker.js", script, filename = "_worker.js", contentType = "application/javascript"),
            ),
        )
    }

    /**
     * Pages custom domain: add it to the project and point a proxied CNAME at
     * its [target] (*.pages.dev) host.
     */
    suspend fun attachPagesDomain(accountId: String, projectName: String, target: String, hostname: String) {
        val zoneId = zoneIdFor(hostname)
        send(
            "POST",
            "/accounts/$accountId/pages/projects/$projectName/domains",
            jsonBody = buildJsonObject { put("name", hostname) },
        )
        val records = send("GET", "/zones/$zoneId/dns_records?name=${urlEnc(hostname)}") as? JsonArray
        val existing = records.orEmpty().mapNotNull { it as? JsonObject }
        val record = buildJsonObject {
            put("type", "CNAME")
            put("name", hostname)
            put("content", target)
            put("proxied", true)
            put("ttl", 1)
        }
        val cname = existing.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "CNAME" }
        if (cname != null) {
            val id = cname["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            send("PUT", "/zones/$zoneId/dns_records/$id", jsonBody = record)
        } else if (existing.any {
                val t = it["type"]?.jsonPrimitive?.contentOrNull
                t == "A" || t == "AAAA"
            }
        ) {
            throw CfException("$hostname already has an A/AAAA record. Remove it in the Cloudflare dashboard first.")
        } else {
            send("POST", "/zones/$zoneId/dns_records", jsonBody = record)
        }
    }

    suspend fun detachPagesDomain(accountId: String, projectName: String, target: String, hostname: String) {
        try {
            send("DELETE", "/accounts/$accountId/pages/projects/$projectName/domains/$hostname")
        } catch (e: CfException) {
            if (isAuthError(e)) throw e // Otherwise it was already removed.
        }
        val zoneId = zoneIdFor(hostname)
        val records = send(
            "GET",
            "/zones/$zoneId/dns_records?type=CNAME&name=${urlEnc(hostname)}",
        ) as? JsonArray ?: return
        // Only remove the record we created (pointing at this project).
        for (element in records) {
            val r = element as? JsonObject ?: continue
            if (r["content"]?.jsonPrimitive?.contentOrNull != target) continue
            val id = r["id"]?.jsonPrimitive?.contentOrNull ?: continue
            send("DELETE", "/zones/$zoneId/dns_records/$id")
        }
    }

    private suspend fun zoneIdFor(hostname: String): String {
        for (name in zoneCandidates(hostname)) {
            val zones = send("GET", "/zones?name=${urlEnc(name)}") as? JsonArray
            val first = zones?.firstOrNull() as? JsonObject
            if (first != null) return first["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        throw CfException(
            "No Cloudflare zone found for $hostname. " +
                "The domain must be on this account and the token needs DNS: Edit.",
        )
    }

    // endregion

    // region usage

    /**
     * Workers requests in the last 24 hours (BPB's GraphQL query, copied
     * verbatim): account total and, for [scriptName], that worker alone —
     * Pages Functions report under the project name. Free plan limit:
     * 100,000 requests/day.
     */
    suspend fun workerUsage(accountId: String, scriptName: String): CfUsage {
        val endMs = currentTimeMillis()
        val startMs = endMs - 24L * 60 * 60 * 1000
        val payload = buildJsonObject {
            put("query", USAGE_QUERY)
            put(
                "variables",
                buildJsonObject {
                    put("accountTag", accountId)
                    put("scriptName", scriptName)
                    put("start", isoUtc(startMs))
                    put("end", isoUtc(endMs))
                },
            )
        }
        val response = httpJson(
            "POST",
            "$BASE/graphql",
            authHeaders + ("Content-Type" to "application/json"),
            payload.toString(),
            readTimeoutMs = 30_000,
            socksPort = socksPort,
        )
        val data = runCatching { Json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
            ?: throw CfException("Usage query failed (HTTP ${response.code})")
        val errors = (data["errors"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull }
        if (errors.isNotEmpty()) throw CfException("Usage query failed: ${errors.joinToString("; ")}")
        val accounts = ((data["data"] as? JsonObject)?.get("viewer") as? JsonObject)
            ?.get("accounts") as? JsonArray
        val account = accounts?.firstOrNull() as? JsonObject
            ?: throw CfException("No usage data for this account")
        fun sum(rows: JsonElement?): Long = (rows as? JsonArray).orEmpty().sumOf { row ->
            val requests = ((row as? JsonObject)?.get("sum") as? JsonObject)
                ?.get("requests")?.jsonPrimitive
            requests?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: 0L
        }
        return CfUsage(total = sum(account["total"]), worker = sum(account["worker"]))
    }

    // endregion

    // region deployments

    /** A random name (8–18 characters) not used yet by a [type] project in the account. */
    suspend fun freeName(accountId: String, type: String): String {
        repeat(5) {
            val name = randomName(8 + (secureRandomBytes(1)[0].toInt() and 0xFF) % 11)
            val path = if (type == TYPE_PAGES) {
                "/accounts/$accountId/pages/projects/$name"
            } else {
                "/accounts/$accountId/workers/scripts/$name/settings"
            }
            try {
                send("GET", path)
            } catch (e: CfException) {
                if (isAuthError(e)) throw e
                return name // Not found: free.
            }
        }
        throw CfException("Could not find a free worker name")
    }

    /**
     * Deploys the Worker [name] with [env] (creating it or replacing it) and
     * turns on its workers.dev route. Returns its workers.dev host.
     */
    suspend fun deployWorker(accountId: String, name: String, env: String, script: String): String {
        val subdomain = ensureSubdomain(accountId)
        uploadScript(accountId, name, env, script)
        enableWorkersDev(accountId, name)
        return "$name.$subdomain.workers.dev"
    }

    /**
     * Deploys the Pages project [name] with [env]: creates it ([create]) or
     * sets its ENV via PATCH, then uploads a deployment. Returns its
     * pages.dev host (from the create/PATCH response's subdomain).
     */
    suspend fun deployPages(accountId: String, name: String, env: String, create: Boolean, script: String): String {
        val production = buildJsonObject {
            put("compatibility_date", currentCompatibilityDate())
            put(
                "env_vars",
                buildJsonObject {
                    put(
                        ENV_VAR,
                        buildJsonObject {
                            put("type", "plain_text")
                            put("value", env)
                        },
                    )
                },
            )
        }
        val project = if (create) {
            send(
                "POST",
                "/accounts/$accountId/pages/projects",
                jsonBody = buildJsonObject {
                    put("name", name)
                    put("production_branch", "main")
                    put("deployment_configs", buildJsonObject { put("production", production) })
                },
            ) as? JsonObject
        } else {
            send(
                "PATCH",
                "/accounts/$accountId/pages/projects/$name",
                jsonBody = buildJsonObject {
                    put("deployment_configs", buildJsonObject { put("production", production) })
                },
            ) as? JsonObject
        }
        uploadPagesDeployment(accountId, name, script)
        return project?.get("subdomain")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    suspend fun attachCustomDomain(account: CfDeploymentRef, hostname: String) {
        if (account.type == TYPE_PAGES) {
            attachPagesDomain(account.accountId, account.name, account.host, hostname)
        } else {
            attachWorkerDomain(account.accountId, account.name, hostname)
        }
    }

    suspend fun detachCustomDomain(account: CfDeploymentRef, hostname: String) {
        if (account.type == TYPE_PAGES) {
            detachPagesDomain(account.accountId, account.name, account.host, hostname)
        } else {
            detachWorkerDomain(account.accountId, account.name, hostname)
        }
    }

    /**
     * Deletes the Worker or Pages deployment from Cloudflare, its custom
     * domains first: a Pages project with a domain can't be deleted, and its
     * CNAME record (which we created) is removed from the zone. Anything
     * already gone is skipped.
     */
    suspend fun deleteDeployment(account: CfDeploymentRef) {
        if (account.type == TYPE_PAGES) {
            val domains = mutableSetOf<String>()
            account.customDomain?.let { domains += it }
            ignoringGone {
                val listed = send(
                    "GET",
                    "/accounts/${account.accountId}/pages/projects/${account.name}/domains",
                ) as? JsonArray
                listed.orEmpty().forEach { element ->
                    (element as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.let { domains += it }
                }
            }
            for (domain in domains) {
                if (domain.isEmpty() || domain == account.host) continue
                ignoringGone { detachPagesDomain(account.accountId, account.name, account.host, domain) }
            }
            ignoringGone { send("DELETE", "/accounts/${account.accountId}/pages/projects/${account.name}") }
        } else {
            ignoringGone {
                val domains = send(
                    "GET",
                    "/accounts/${account.accountId}/workers/domains?service=${urlEnc(account.name)}",
                ) as? JsonArray
                for (element in domains.orEmpty()) {
                    val d = element as? JsonObject ?: continue
                    val id = d["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    send("DELETE", "/accounts/${account.accountId}/workers/domains/$id")
                }
            }
            ignoringGone {
                send("DELETE", "/accounts/${account.accountId}/workers/scripts/${account.name}?force=true")
            }
        }
    }

    private suspend fun ignoringGone(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CfException) {
            if (!isGone(e)) throw e
        }
    }

    // endregion

    companion object {
        private const val BASE = "https://api.cloudflare.com/client/v4"

        /** The bundled worker's version, bumped with every change to worker.js (BPB parity). */
        const val WORKER_VERSION = 1

        /** Deployments older than this are redeployed automatically (BPB parity). */
        const val MIN_WORKER_VERSION = 1

        /** The worker's only binding (plain text): base64 of a JSON object, see [encodeEnv]. */
        const val ENV_VAR = "ENV"

        const val TYPE_WORKERS = "workers"
        const val TYPE_PAGES = "pages"

        /** Free-plan request cap per day, for the usage card (BPB parity). */
        const val DAILY_REQUEST_LIMIT = 100_000L

        private const val USAGE_QUERY = """
query GetUsage(${'$'}accountTag: String!, ${'$'}scriptName: String!, ${'$'}start: String!, ${'$'}end: String!) {
  viewer {
    accounts(filter: { accountTag: ${'$'}accountTag }) {
      total: workersInvocationsAdaptive(limit: 100, filter: { datetime_geq: ${'$'}start, datetime_leq: ${'$'}end }) {
        sum { requests }
      }
      worker: workersInvocationsAdaptive(
        limit: 100
        filter: { scriptName: ${'$'}scriptName, datetime_geq: ${'$'}start, datetime_leq: ${'$'}end }
      ) {
        sum { requests }
      }
    }
  }
}
"""

        /** The bundled worker script (BPB's worker.js, bundled as-is; see files/bpb/NOTICE.txt). */
        suspend fun loadWorkerScript(): String =
            Res.readBytes("files/bpb/worker.js").decodeToString()

        /** ENV's value: `{v, id, pips, fb}` as JSON, base64 (standard alphabet, BPB parity). */
        fun encodeEnv(uuid: String, proxyIps: List<String>, fallbackUrl: String): String {
            val json = buildJsonObject {
                put("v", 1)
                put("id", uuid)
                put(
                    "pips",
                    buildJsonArray { proxyIps.forEach { add(JsonPrimitive(it)) } },
                )
                put("fb", fallbackUrl)
            }
            return Base64.encode(json.toString().encodeToByteArray())
        }

        /** Token creation page with the permissions pre-selected (same set as BPB-Wizard). */
        fun tokenCreationUrl(): String {
            val permissions = """[{"key":"workers_scripts","type":"edit"},{"key":"page","type":"edit"},{"key":"dns","type":"edit"},{"key":"user_details","type":"read"}]"""
            return "https://dash.cloudflare.com/profile/api-tokens" +
                "?permissionGroupKeys=${urlEnc(permissions)}&accountId=*&zoneId=all&name=BPB-VPN"
        }

        /**
         * Extracts the API token from pasted text (BPB's normalizeToken):
         * strips everything outside `[A-Za-z0-9_-]`, takes the part after
         * "Bearer" from a pasted curl command, rejoins line-wrapped tokens.
         * Throws for a Global API Key, which can't be used as a Bearer token.
         */
        fun normalizeToken(input: String): String {
            var text = input
            val bearer = text.lowercase().lastIndexOf("bearer")
            if (bearer >= 0) {
                text = text.substring(bearer + 6).trimStart()
                val quote = text.indexOfAny(charArrayOf('"', '\''))
                if (quote > 0) text = text.substring(0, quote)
            }
            val token = text.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            if (token.isEmpty()) throw CfException("Enter your API token")
            if (Regex("^[0-9a-f]{37}$").matches(token)) {
                throw CfException(
                    "This looks like the Global API Key. Create an API token instead (use the button above).",
                )
            }
            if (token.length < 30) {
                throw CfException("That doesn't look like a Cloudflare API token. Copy the token itself.")
            }
            return token
        }

        /** A fresh VLESS UUID (BPB: one per account, shared by both deployments). */
        fun generateUuid(): String {
            val bytes = secureRandomBytes(16)
            bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
            val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) +
                "-" + hex.substring(16, 20) + "-" + hex.substring(20)
        }

        /** Random lowercase alphanumeric name (BPB's _randomName, secure random). */
        fun randomName(length: Int): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val bytes = secureRandomBytes(length)
            return buildString(length) {
                bytes.forEach { append(chars[(it.toInt() and 0xFF) % chars.length]) }
            }
        }

        /** A fresh WS path for a deployment: "/" + 16–32 random chars + "?ed=2560" (BPB's _wsPath rule). */
        fun newWsPath(): String {
            val length = 16 + (secureRandomBytes(1)[0].toInt() and 0xFF) % 17
            return "/" + randomName(length) + "?ed=2560"
        }

        internal fun zoneCandidates(hostname: String): List<String> {
            val labels = hostname.split('.')
            return (0..labels.size - 2).map { i -> labels.subList(i, labels.size).joinToString(".") }
        }

        /** The thing asked for doesn't exist (any more). */
        internal fun isGone(e: CfException): Boolean {
            val m = e.message.orEmpty().lowercase()
            return m.contains("not found") || m.contains("not_found") || m.contains("does not exist")
        }

        internal fun isAuthError(e: CfException): Boolean {
            val m = e.message.orEmpty()
            return m.contains("(10000)") || m.contains("(9109)") || m.contains("(6003)")
        }

        private fun currentCompatibilityDate(): String = isoUtc(currentTimeMillis()).substring(0, 10)

        /** RFC 3339 UTC timestamp for [millis] (GraphQL filters). */
        internal fun isoUtc(millis: Long): String {
            val totalSeconds = millis / 1000
            val days = floorDiv(totalSeconds, 86_400L)
            val secsOfDay = totalSeconds - days * 86_400L
            // Civil-from-days (Howard Hinnant's algorithm).
            val z = days + 719_468
            val era = floorDiv(z, 146_097L)
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val day = doy - (153 * mp + 2) / 5 + 1
            val month = if (mp < 10) mp + 3 else mp - 9
            val year = if (month <= 2) y + 1 else y
            val hour = secsOfDay / 3600
            val minute = (secsOfDay % 3600) / 60
            val second = secsOfDay % 60
            fun pad(v: Long, w: Int) = v.toString().padStart(w, '0')
            return "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}T${pad(hour, 2)}:${pad(minute, 2)}:${pad(second, 2)}.000Z"
        }

        private fun floorDiv(a: Long, b: Long): Long {
            val q = a / b
            return if (a % b != 0L && (a < 0) != (b < 0)) q - 1 else q
        }

        private fun urlEnc(value: String): String {
            val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
            val hexDigits = "0123456789ABCDEF"
            val sb = StringBuilder()
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt() and 0xFF
                if (c < 128 && unreserved.indexOf(c.toChar()) >= 0) {
                    sb.append(c.toChar())
                } else {
                    sb.append('%').append(hexDigits[c shr 4]).append(hexDigits[c and 0x0F])
                }
            }
            return sb.toString()
        }
    }
}

/** Identifies one deployment for the domain/delete calls (BPB's ActiveWorker, trimmed). */
data class CfDeploymentRef(
    val accountId: String,
    val type: String,
    val name: String,
    val host: String,
    val customDomain: String? = null,
)

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
