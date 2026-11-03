package com.amyrmo.damavand.logic

import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AetherPsiphon
import com.amyrmo.damavand.model.AetherTor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The verbatim-command machinery (§6.2 — Narcic's AetherCommandsTest
 * cases, ported): quote-aware word splitting, listener adoption and
 * repair, log-level/info-line guarantees, the editor's problem rules,
 * and `--upstream` non-duplication.
 */
class AetherCommandsTest {

    // ---------- word splitting ----------

    @Test
    fun `words respects quotes`() {
        assertEquals(
            listOf("aether", "--bind", "127.0.0.1:1080", "--finalmask", "udp:a b"),
            AetherCommands.words("""aether --bind 127.0.0.1:1080 --finalmask "udp:a b""""),
        )
        assertEquals(
            listOf("--tor-bridge", "obfs4 1.2.3.4:443 cert=x iat-mode=0"),
            AetherCommands.words("--tor-bridge 'obfs4 1.2.3.4:443 cert=x iat-mode=0'"),
        )
    }

    @Test
    fun `argumentsOf drops the program name only`() {
        assertEquals(listOf("--protocol", "wg"), AetherCommands.argumentsOf("aether --protocol wg"))
        assertEquals(listOf("--protocol", "wg"), AetherCommands.argumentsOf("--protocol wg"))
        assertEquals(emptyList<String>(), AetherCommands.argumentsOf("aether"))
    }

    // ---------- listener handling ----------

    @Test
    fun `listener port is adopted from the command`() {
        val argv = AetherCommands.argumentsOf("aether --bind 127.0.0.1:19393 --protocol wg")
        assertEquals(19393, AetherCommands.listenerPortOf(argv))
    }

    @Test
    fun `custom arguments keep the command's own listener`() {
        val profile = AetherProfile(
            id = "c1", name = "c",
            command = "aether --bind 127.0.0.1:19393 --protocol wg --peer 1.2.3.4:443",
        )
        val args = AetherCommands.customArguments(profile, port = 18190)
        assertEquals("127.0.0.1:19393", args[args.indexOf("--bind") + 1])
        assertFalse(args.contains("127.0.0.1:18190"))
    }

    @Test
    fun `custom arguments add a listener when the command names none`() {
        val profile = AetherProfile(id = "c2", name = "c", command = "aether --protocol wg")
        val args = AetherCommands.customArguments(profile, port = 18190)
        assertEquals("127.0.0.1:18190", args[args.indexOf("--bind") + 1])
        assertEquals("info", args[args.indexOf("--log-level") + 1])
    }

    @Test
    fun `psiphon chain command listens on the psiphon bind`() {
        val argv = listOf("--psiphon", "--psiphon-bind", "127.0.0.1:19001")
        assertEquals("--psiphon-bind", AetherCommands.listenerFlagOf(argv))
        assertEquals(19001, AetherCommands.listenerPortOf(argv))
    }

    @Test
    fun `withListener replaces rather than duplicates`() {
        val out = AetherCommands.withListener(listOf("--bind", "127.0.0.1:1", "--protocol", "wg"), "--bind", 2222)
        assertEquals(1, out.count { it == "--bind" })
        assertEquals("127.0.0.1:2222", out[out.indexOf("--bind") + 1])
    }

    @Test
    fun `withoutOption removes flag and value pairs`() {
        assertEquals(
            listOf("--protocol", "wg"),
            AetherCommands.withoutOption(listOf("--log-level", "warn", "--protocol", "wg", "--log-level", "info"), "--log-level"),
        )
    }

    // ---------- upstream is never duplicated ----------

    @Test
    fun `a baked upstream stays single`() {
        val profile = AetherProfile(
            id = "c3", name = "c",
            command = "aether --protocol wg --upstream socks5://127.0.0.1:19000",
        )
        val args = AetherCommands.customArguments(profile, port = 18190)
        assertEquals(1, args.count { it == "--upstream" })
        assertEquals("socks5://127.0.0.1:19000", args[args.indexOf("--upstream") + 1])
    }

    // ---------- readiness / log level ----------

    @Test
    fun `psiphon sessions need the ready word and get info lines`() {
        assertTrue(AetherCommands.readyNeedsWord(listOf("--psiphon")))
        assertTrue(AetherCommands.readyNeedsWord(listOf("--psiphon-only")))
        assertFalse(AetherCommands.readyNeedsWord(listOf("--protocol", "wg")))

        val profile = AetherProfile(
            id = "c4", name = "c",
            command = "aether --psiphon --log-level warn",
        )
        val args = AetherCommands.customArguments(profile, port = 18190)
        assertTrue(AetherCommands.showsInfo(args), args.toString())
    }

    @Test
    fun `last mode flag wins`() {
        assertEquals(AetherTor.ONLY, AetherCommands.torModeOf(listOf("--tor", "--tor-only")))
        assertEquals(AetherPsiphon.REVERSE, AetherCommands.psiphonModeOf(listOf("--psiphon", "--psiphon-reverse")))
        assertEquals(AetherTor.OFF, AetherCommands.torModeOf(listOf("--protocol", "wg")))
    }

    // ---------- editor problem rules ----------

    @Test
    fun `problemOf carrier needs masque`() {
        val p = AetherProfile(id = "p1", name = "x", protocol = "wg", tor = "reverse")
        assertEquals(AetherCommands.Problem.CARRIER_NEEDS_MASQUE, AetherCommands.problemOf(p))
        val ok = AetherProfile(id = "p2", name = "x", protocol = "masque", tor = "reverse")
        assertNull(AetherCommands.problemOf(ok))
    }

    @Test
    fun `problemOf tor psiphon conflict`() {
        val p = AetherProfile(id = "p3", name = "x", protocol = "masque", tor = "chain", psiphon = "chain")
        assertEquals(AetherCommands.Problem.TOR_PSIPHON_CONFLICT, AetherCommands.problemOf(p))
        val nested = AetherProfile(id = "p4", name = "x", protocol = "masque", tor = "chain", psiphon = "reverse")
        assertNull(AetherCommands.problemOf(nested))
    }

    @Test
    fun `problemOf own bridges need lines`() {
        val p = AetherProfile(id = "p5", name = "x", tor = "chain", torBridges = "own", torBridgeLines = "  \n")
        assertEquals(AetherCommands.Problem.TOR_BRIDGES_MISSING, AetherCommands.problemOf(p))
        val ok = p.copy(torBridgeLines = "obfs4 1.2.3.4:443 AA cert=x")
        assertNull(AetherCommands.problemOf(ok))
    }

    @Test
    fun `isCustom follows the command field`() {
        assertFalse(AetherCommands.isCustom(AetherProfile(id = "p6", name = "x")))
        assertTrue(AetherCommands.isCustom(AetherProfile(id = "p7", name = "x", command = "aether --protocol wg")))
    }
}
