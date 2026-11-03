package com.amyrmo.damavand.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The registry test (architecture §6.2): EVERY [Profile] variant must
 * round-trip through kotlinx.serialization. The variant list is derived
 * reflectively from the sealed hierarchy, so a future variant that forgets
 * `@Serializable`/`@SerialName` — v1's Mihomo bug, where profiles could
 * not persist at all — fails here instead of silently losing user data.
 */
class ProfileSerializationTest {

    private val json = ProfileJson.json

    /** One representative instance per variant, keyed by the variant class. */
    private val samples: Map<Class<*>, Profile> = listOf<Profile>(
        XrayProfile(
            id = "id-xray-link", name = "Xray link", subscriptionId = "sub-1",
            payload = XrayPayload.Link("vless://uuid@h.example:443?security=tls#x"),
            dns = "https://dns.example/dns-query",
        ),
        XrayProfile(
            id = "id-xray-json", name = "Xray json",
            payload = XrayPayload.RawJson("""{"outbounds":[]}"""),
        ),
        XrayProfile(
            id = "id-xray-sealed", name = "Xray sealed",
            payload = XrayPayload.Sealed("enxleA=="),
        ),
        SingBoxProfile(
            id = "id-singbox-full", name = "SB full",
            json = """{"outbounds":[{"type":"vless","tag":"proxy"}]}""", fragment = false,
        ),
        SingBoxProfile(
            id = "id-singbox-frag", name = "SB fragment", subscriptionId = "sub-1",
            json = """{"outbounds":[{"type":"tuic","tag":"t"}]}""", fragment = true,
            sourceLink = "tuic://u:p@h.example:443#t",
        ),
        PsiphonProfile(
            id = "id-psiphon", name = "Psiphon CA", country = "CA", mode = PsiphonMode.CDN,
            cdnIps = listOf("104.16.0.0/12", "172.64.0.1"), cdnSni = "cdn.example.com",
        ),
        AetherProfile(
            id = "id-aether", name = "Aether amir",
            protocol = "masque", transport = "h2", ipVersion = "both",
            address = "162.159.199.42", port = "443",
            psiphon = "chain", command = "aether --bind 127.0.0.1:18190 --protocol wg",
        ),
        MihomoProfile(
            id = "id-mihomo", name = "Mihomo sub", subUrl = "https://subs.example/clash.yaml",
            logLevel = "debug", dns = "1.1.1.1",
        ),
        WhitednsProfile(
            id = "id-whitedns", name = "DNS tunnel",
            engine = WhitednsEngine.COTTEN,
            domains = listOf("v.example.com", "v2.example.com"),
            encryptionKey = "secret-key", encryptionMethod = 3,
            resolvers = listOf("1.1.1.1", "8.8.8.8:53", "[2001:4860:4860::8888]:53"),
            tunnelMode = WhitednsMode.COTTENMAX, resolverTransport = "doh",
            balancingStrategy = 1, dns = "9.9.9.9",
        ),
        ChainProfile(
            id = "id-chain", name = "WD → Aether → Xray",
            hopIds = listOf("id-whitedns", "id-aether", "id-xray-link"),
        ),
        AutoSelectProfile(
            id = "id-auto", name = "Smart", targetSubscriptionId = "sub-1",
        ),
    ).associateBy { it.javaClass }

    @Test
    fun `every sealed Profile variant has a sample`() {
        val variants = Profile::class.java.let { sealedSubclasses() }
        val missing = variants - samples.keys
        assertTrue(missing.isEmpty(), "Profile variants without a round-trip sample: $missing")
    }

    @Test
    fun `every Profile variant round-trips through serialization`() {
        for ((clazz, sample) in samples) {
            val encoded = json.encodeToString(Profile.serializer(), sample)
            val decoded = json.decodeFromString(Profile.serializer(), encoded)
            assertEquals(sample, decoded, "Round-trip mismatch for ${clazz.simpleName}: $encoded")
            assertEquals(sample.kind, decoded.kind, "Kind mismatch for ${clazz.simpleName}")
        }
    }

    @Test
    fun `serial names on disk are the spec names`() {
        val expected = mapOf(
            "id-xray-link" to "xray",
            "id-singbox-full" to "singbox",
            "id-psiphon" to "psiphon",
            "id-aether" to "aether",
            "id-mihomo" to "mihomo",
            "id-whitedns" to "whitedns",
            "id-chain" to "chain",
            "id-auto" to "auto",
        )
        for (sample in samples.values) {
            val type = json.encodeToJsonElement(Profile.serializer(), sample)
                .jsonObject["type"]?.jsonPrimitive?.content
            val want = expected[sample.id] ?: continue
            assertEquals(want, type, "Wrong @SerialName for ${sample.id}")
        }
    }

    @Test
    fun `profile list envelope round-trips`() {
        val all = samples.values.toList()
        val encoded = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Profile.serializer()),
            all,
        )
        val decoded = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(Profile.serializer()),
            encoded,
        )
        assertEquals(all, decoded)
    }

    @Test
    fun `xray payload variants round-trip`() {
        val payloads = listOf(
            XrayPayload.Link("trojan://pw@h:443#t"),
            XrayPayload.RawJson("{}"),
            XrayPayload.Sealed("AAAA"),
        )
        for (payload in payloads) {
            val encoded = json.encodeToString(XrayPayload.serializer(), payload)
            assertEquals(payload, json.decodeFromString(XrayPayload.serializer(), encoded))
        }
    }

    @Test
    fun `unknown keys are tolerated for forward compatibility`() {
        val future = """{"type":"mihomo","id":"m1","name":"M","yaml":"proxies: []","futureField":42}"""
        val decoded = json.decodeFromString(Profile.serializer(), future)
        assertTrue(decoded is MihomoProfile)
        assertEquals("proxies: []", (decoded as MihomoProfile).yaml)
    }

    private fun sealedSubclasses(): Set<Class<*>> {
        // Discover the variants by scanning this module's own classpath
        // output for top-level classes implementing Profile, so a new
        // variant is enrolled automatically (and this test then demands
        // a sample for it). Classpath scanning is used rather than
        // Class.getPermittedSubclasses(): kotlinc does not emit the JVM
        // PermittedSubclasses attribute for sealed interfaces, so the
        // reflective call returns null for Profile even though the
        // hierarchy is sealed at the language level.
        val pkg = "com.amyrmo.damavand.model"
        val pkgPath = pkg.replace('.', '/')
        val location = java.io.File(Profile::class.java.protectionDomain.codeSource.location.toURI())
        val classNames = sortedSetOf<String>()
        if (location.isDirectory) {
            java.io.File(location, pkgPath).listFiles()
                ?.filter { it.name.endsWith(".class") && '$' !in it.name }
                ?.forEach { classNames += "$pkg." + it.name.removeSuffix(".class") }
        } else {
            java.util.jar.JarFile(location).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.startsWith("$pkgPath/") && it.name.endsWith(".class") && '$' !in it.name }
                    .forEach { classNames += it.name.removeSuffix(".class").replace('/', '.') }
            }
        }
        val variants = classNames
            .mapNotNull { runCatching { Class.forName(it) }.getOrNull() }
            .filter { it != Profile::class.java && Profile::class.java.isAssignableFrom(it) }
            .toSet()
        assertTrue(variants.isNotEmpty(), "Variant discovery found nothing — the scan is broken")
        return variants
    }

    @Test
    fun `envelope shape parses as json object`() {
        val element = json.encodeToJsonElement(Profile.serializer(), samples.values.first())
        assertTrue(element is JsonObject)
    }
}
