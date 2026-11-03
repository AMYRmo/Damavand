package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.MihomoProfile
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Golden tests for the structured Mihomo editor (§3.4). The canonical form
 * is YamlEmitter's output, so goldens compare as exact strings; every
 * output is additionally re-parsed with kaml to prove it stays valid YAML
 * with equivalent content.
 */
class MihomoConfigEditorTest {

    private fun golden(name: String): String =
        requireNotNull(javaClass.getResource("/golden/$name")) { "missing golden $name" }.readText()

    private val userYaml = """
        mixed-port: 7890
        allow-lan: true
        mode: global
        log-level: debug
        proxies:
          - name: "node-1"
            type: vless
            server: vl.example.com
            port: 443
            uuid: b831381d-6324-4d53-ad4f-8cda48b30811
          - name: "node-2"
            type: trojan
            server: tj.example.com
            port: 443
            password: s3cret
        proxy-groups:
          - name: "Proxy"
            type: select
            proxies:
              - node-1
              - node-2
        rules:
          - DOMAIN-SUFFIX,ir,DIRECT
          - MATCH,Proxy
    """.trimIndent()

    private fun profile(yaml: String = userYaml, subUrl: String = "") =
        MihomoProfile(id = "m1", name = "M", yaml = yaml, subUrl = subUrl)

    @Test
    fun `user yaml golden - scalars set in place, user mode kept`() {
        val out = MihomoConfigEditor.prepare(profile(), mixedPort = 18210, upstreamSocksPort = null)
        assertEquals(golden("mihomo-user.yaml"), out)
        val root = Yaml.default.parseToYamlNode(out).yamlMap
        assertEquals("18210", root.getScalar("mixed-port")?.content)
        assertEquals("false", root.getScalar("allow-lan")?.content)
        assertEquals("global", root.getScalar("mode")?.content, "user mode is not overridden")
        assertEquals("info", root.getScalar("log-level")?.content, "profile log level wins")
    }

    @Test
    fun `chain wiring golden - dialer-proxy on every proxy plus prev-hop`() {
        val out = MihomoConfigEditor.prepare(profile(), mixedPort = 18210, upstreamSocksPort = 18195)
        assertEquals(golden("mihomo-chain.yaml"), out)
        assertTrue(out.contains("dialer-proxy: prev-hop"))
        assertTrue(out.contains("name: prev-hop"))
    }

    @Test
    fun `subscription profile golden - provider document generated structurally`() {
        val out = MihomoConfigEditor.prepare(
            profile(yaml = "", subUrl = "https://sub.example.com/clash"),
            mixedPort = 18210, upstreamSocksPort = null,
        )
        assertEquals(golden("mihomo-sub.yaml"), out)
    }

    @Test
    fun `emitted yaml re-parses to equivalent content`() {
        val out = MihomoConfigEditor.prepare(profile(), mixedPort = 18210, upstreamSocksPort = 18195)
        val reparsed = Yaml.default.parseToYamlNode(out)
        val goldenParsed = Yaml.default.parseToYamlNode(golden("mihomo-chain.yaml"))
        assertTrue(reparsed.equivalentContentTo(goldenParsed))
    }

    @Test
    fun `config without proxies or providers is rejected`() {
        assertThrows<MihomoConfigException> {
            MihomoConfigEditor.prepare(profile(yaml = "mode: rule\nrules: []"), 18210, null)
        }
        assertFalse(MihomoConfigEditor.hasDialableProxy("mode: rule"))
        assertTrue(MihomoConfigEditor.hasDialableProxy(userYaml))
    }

    @Test
    fun `empty profile is rejected`() {
        assertThrows<MihomoConfigException> {
            MihomoConfigEditor.prepare(profile(yaml = "", subUrl = ""), 18210, null)
        }
    }

    @Test
    fun `udp heuristic sees udp-carrying proxy types`() {
        val hy2 = "proxies:\n  - name: h\n    type: hysteria2\n    server: hy.example.com\n    port: 443"
        assertTrue(MihomoConfigEditor.needsUdp(hy2))
        assertFalse(MihomoConfigEditor.needsUdp(userYaml))
    }
}
