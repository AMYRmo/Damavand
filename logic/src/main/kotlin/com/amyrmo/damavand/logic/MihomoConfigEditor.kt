package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.MihomoProfile
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlPath
import com.charleskorn.kaml.YamlScalar

class MihomoConfigException(message: String) : Exception(message)

/**
 * Structured Mihomo config preparation (architecture §3.4) via kaml: the
 * user's YAML is parsed into a node tree, edited as a tree, and serialised
 * back — v1's trailing-string appends (which broke on user formatting and
 * duplicated keys) are gone.
 *
 * What prepare() guarantees, standalone or in a chain:
 *  - `mixed-port` = the allocated port (the port the plan advertises is the
 *    port in the config, §2.4 rule 2), loopback-only binding;
 *  - app-managed scalars (mode default, log level, controller off);
 *  - the bypass rules v1 appended (`GEOIP,IR,DIRECT`, `GEOIP,private,DIRECT`)
 *    present exactly once, and a terminal `MATCH` rule;
 *  - chain wiring per §4.4: a synthetic `prev-hop` SOCKS proxy and
 *    `dialer-proxy: prev-hop` on every real proxy and proxy-provider.
 *
 * Deliberate deviations from v1 (documented per §3.4's "structured edit"
 * mandate): an existing user `mode` is kept instead of being overridden by
 * an appended duplicate; the user's `dns` section is left as authored (the
 * bridge owns session DNS, §4.6); scalar values are set in place rather
 * than appended. Scalar STYLE is normalised on emit (kaml's tree does not
 * retain source quoting): canonical numbers/booleans are written plain —
 * a user string in a string-typed field that is exactly a canonical
 * number or `true`/`false` loses its quotes (see YamlEmitter).
 */
object MihomoConfigEditor {

    /** Name of the synthetic previous-hop proxy injected for chain wiring. */
    const val PREV_HOP_NAME = "prev-hop"

    private val BYPASS_RULES = listOf("GEOIP,IR,DIRECT", "GEOIP,private,DIRECT")

    /** Proxy types whose traffic is UDP-carried (capability heuristic). */
    private val UDP_TYPES = setOf("wireguard", "hysteria", "hysteria2", "hy2", "tuic")

    /**
     * The runnable config for [profile]: [mixedPort] is the hop's listener;
     * [upstreamSocksPort], when non-null, is the previous hop's listener and
     * is wired in via `dialer-proxy`.
     *
     * @throws MihomoConfigException on unparseable YAML or a config with no
     * dialable proxy/provider.
     */
    fun prepare(profile: MihomoProfile, mixedPort: Int, upstreamSocksPort: Int?): String {
        val root = when {
            profile.yaml.isNotBlank() -> parseRoot(profile.yaml)
            profile.subUrl.isNotBlank() -> providerDocument(profile.subUrl)
            else -> throw MihomoConfigException("Mihomo profile has neither a YAML config nor a subscription URL")
        }
        val out = editRoot(root, profile, mixedPort, upstreamSocksPort)
        // kaml's node tree has no serialiser in 0.62; YamlEmitter writes
        // the canonical block form (round-trip tested).
        return YamlEmitter.emit(out)
    }

    /** True when the YAML parses and declares at least one proxy or provider. */
    fun hasDialableProxy(yaml: String): Boolean = runCatching {
        val root = parseRoot(yaml)
        proxiesOf(root).isNotEmpty() || providersOf(root).isNotEmpty()
    }.getOrDefault(false)

    /** Heuristic for validation rule V5: any proxy of a UDP-carried type. */
    fun needsUdp(yaml: String): Boolean = runCatching {
        val root = parseRoot(yaml)
        proxiesOf(root).any { proxy ->
            ((proxy as? YamlMap)?.getScalar("type")?.content?.lowercase() ?: "") in UDP_TYPES
        }
    }.getOrDefault(false)

    // -- document construction -------------------------------------------------

    private fun parseRoot(yaml: String): YamlMap {
        val node = try {
            Yaml.default.parseToYamlNode(yaml)
        } catch (e: Exception) {
            throw MihomoConfigException("unparseable Mihomo YAML: ${e.message}")
        }
        return node as? YamlMap ?: throw MihomoConfigException("Mihomo YAML root must be a mapping")
    }

    /** The subscription-backed document v1 synthesised, built structurally. */
    private fun providerDocument(subUrl: String): YamlMap = yamlMapOf(
        "proxy-providers" to yamlMapOf(
            "sub" to yamlMapOf(
                "type" to scalar("http"),
                "url" to scalar(subUrl),
                "interval" to scalar("3600"),
                "health-check" to yamlMapOf(
                    "enable" to scalar("true"),
                    "url" to scalar("https://www.gstatic.com/generate_204"),
                    "interval" to scalar("300"),
                ),
            ),
        ),
        "proxy-groups" to YamlList(
            listOf(
                yamlMapOf(
                    "name" to scalar("Auto"),
                    "type" to scalar("url-test"),
                    "use" to YamlList(listOf(scalar("sub")), YamlPath.root),
                    "url" to scalar("https://www.gstatic.com/generate_204"),
                    "interval" to scalar("300"),
                ),
            ),
            YamlPath.root,
        ),
    )

    // -- the edits ---------------------------------------------------------------

    private fun editRoot(root: YamlMap, profile: MihomoProfile, mixedPort: Int, upstreamSocksPort: Int?): YamlMap {
        val entries = LinkedHashMap<YamlScalar, YamlNode>()
        root.entries.forEach { (k, v) -> entries[k] = v }

        fun set(key: String, value: String) {
            val existing = entries.keys.firstOrNull { it.content == key }
            entries[existing ?: YamlScalar(key, YamlPath.root)] = YamlScalar(value, YamlPath.root)
        }
        fun get(key: String): YamlNode? = entries.entries.firstOrNull { it.key.content == key }?.value
        fun put(key: String, node: YamlNode) {
            val existing = entries.keys.firstOrNull { it.content == key }
            entries[existing ?: YamlScalar(key, YamlPath.root)] = node
        }

        set("mixed-port", mixedPort.toString())
        set("allow-lan", "false")
        set("bind-address", "127.0.0.1")
        if (get("mode") == null) set("mode", "rule")
        set("log-level", profile.logLevel)
        set("external-controller", "")
        set("ipv6", "true")
        set("unified-delay", "true")
        set("tcp-concurrent", "true")

        // Chain wiring (§4.4): synthetic previous hop + dialer-proxy on
        // every real proxy and every provider.
        if (upstreamSocksPort != null) {
            val proxies = proxiesOf(YamlMap(entries, YamlPath.root)).map { node ->
                val map = node as? YamlMap ?: return@map node
                if (map.getScalar("name")?.content == PREV_HOP_NAME) map else withDialerProxy(map)
            }
            put(
                "proxies",
                YamlList(
                    proxies + yamlMapOf(
                        "name" to scalar(PREV_HOP_NAME),
                        "type" to scalar("socks5"),
                        "server" to scalar("127.0.0.1"),
                        "port" to scalar(upstreamSocksPort.toString()),
                        "udp" to scalar("true"),
                    ),
                    YamlPath.root,
                ),
            )
            val providers = (get("proxy-providers") as? YamlMap)?.let { provs ->
                val mapped = LinkedHashMap<YamlScalar, YamlNode>()
                provs.entries.forEach { (k, v) ->
                    mapped[k] = (v as? YamlMap)?.let { withDialerProxy(it) } ?: v
                }
                YamlMap(mapped, YamlPath.root)
            }
            if (providers != null) put("proxy-providers", providers)
        }

        // Rules: bypass rules exactly once; a terminal MATCH if none.
        val ruleItems = ((get("rules") as? YamlList)?.items ?: emptyList()).toMutableList()
        val texts = ruleItems.map { (it as? YamlScalar)?.content }.toMutableList()
        var insertAt = texts.indexOfFirst { it?.startsWith("MATCH") == true }
            .let { if (it >= 0) it else ruleItems.size }
        BYPASS_RULES.forEach { rule ->
            if (rule !in texts) {
                ruleItems.add(insertAt, YamlScalar(rule, YamlPath.root))
                texts.add(insertAt, rule)
                insertAt++
            }
        }
        if (texts.none { it?.startsWith("MATCH") == true }) {
            val group = firstGroupName(YamlMap(entries, YamlPath.root))
            if (group != null) {
                ruleItems.add(YamlScalar("MATCH,$group", YamlPath.root))
            }
        }
        put("rules", YamlList(ruleItems, YamlPath.root))

        val result = YamlMap(entries, YamlPath.root)
        if (proxiesOf(result).isEmpty() && providersOf(result).isEmpty()) {
            throw MihomoConfigException("Mihomo config has no proxies or proxy-providers")
        }
        return result
    }

    private fun withDialerProxy(map: YamlMap): YamlMap {
        val entries = LinkedHashMap<YamlScalar, YamlNode>()
        map.entries.forEach { (k, v) -> entries[k] = v }
        val existing = entries.keys.firstOrNull { it.content == "dialer-proxy" }
        entries[existing ?: YamlScalar("dialer-proxy", YamlPath.root)] = YamlScalar(PREV_HOP_NAME, YamlPath.root)
        return YamlMap(entries, YamlPath.root)
    }

    // -- accessors ---------------------------------------------------------------

    private fun proxiesOf(root: YamlMap): List<YamlNode> =
        (root.entries.entries.firstOrNull { it.key.content == "proxies" }?.value as? YamlList)?.items ?: emptyList()

    private fun providersOf(root: YamlMap): List<YamlNode> =
        (root.entries.entries.firstOrNull { it.key.content == "proxy-providers" }?.value as? YamlMap)
            ?.entries?.values?.toList() ?: emptyList()

    private fun firstGroupName(root: YamlMap): String? {
        val groups = (root.entries.entries.firstOrNull { it.key.content == "proxy-groups" }?.value as? YamlList)?.items
            ?: return null
        return groups.firstNotNullOfOrNull { (it as? YamlMap)?.getScalar("name")?.content }
    }

    // -- node builders -------------------------------------------------------------

    private fun scalar(value: String) = YamlScalar(value, YamlPath.root)

    private fun yamlMapOf(vararg pairs: Pair<String, YamlNode>): YamlMap {
        val entries = LinkedHashMap<YamlScalar, YamlNode>()
        pairs.forEach { (k, v) -> entries[YamlScalar(k, YamlPath.root)] = v }
        return YamlMap(entries, YamlPath.root)
    }
}
