package com.amyrmo.damavand.logic

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode

/**
 * Deterministic YAML writer for kaml node trees. kaml 0.62 parses YAML into
 * a typed tree but ships no node serialiser, so config generators that edit
 * trees ([MihomoConfigEditor]) emit through here.
 *
 * Canonical form: block style, 2-space indent, mapping keys in tree order,
 * scalars plain unless they would not re-parse as the same string (then
 * double-quoted with escapes). Re-parsing the output with kaml yields a
 * tree with equivalent content — the round-trip is covered by tests.
 */
internal object YamlEmitter {

    fun emit(node: YamlNode): String {
        val sb = StringBuilder()
        writeNode(sb, node, indent = 0, topLevel = true)
        return sb.toString()
    }

    private fun writeNode(sb: StringBuilder, node: YamlNode, indent: Int, topLevel: Boolean = false) {
        when (val n = unwrap(node)) {
            is YamlMap -> {
                if (n.entries.isEmpty()) {
                    sb.append("{}\n")
                    return
                }
                n.entries.forEach { (key, value) ->
                    sb.append(" ".repeat(indent)).append(scalarText(key.content)).append(":")
                    when (val v = unwrap(value)) {
                        is YamlScalar, is YamlNull -> sb.append(" ").append(scalarTextOrNull(v)).append("\n")
                        is YamlMap -> if (v.entries.isEmpty()) sb.append(" {}\n") else { sb.append("\n"); writeNode(sb, v, indent + 2) }
                        is YamlList -> if (v.items.isEmpty()) sb.append(" []\n") else { sb.append("\n"); writeNode(sb, v, indent + 2) }
                        else -> sb.append("\n")
                    }
                }
            }
            is YamlList -> {
                if (n.items.isEmpty()) {
                    sb.append("[]\n")
                    return
                }
                n.items.forEach { item ->
                    when (val v = unwrap(item)) {
                        is YamlScalar, is YamlNull -> sb.append(" ".repeat(indent)).append("- ").append(scalarTextOrNull(v)).append("\n")
                        is YamlMap -> {
                            if (v.entries.isEmpty()) {
                                sb.append(" ".repeat(indent)).append("- {}\n")
                            } else {
                                // First key rides on the "- " line; the rest
                                // align under it (standard block style).
                                val inner = StringBuilder()
                                writeNode(inner, v, indent + 2)
                                val text = inner.toString()
                                sb.append(" ".repeat(indent)).append("- ")
                                    .append(text.substring(indent + 2))
                            }
                        }
                        is YamlList -> {
                            sb.append(" ".repeat(indent)).append("-\n")
                            writeNode(sb, v, indent + 2)
                        }
                        else -> Unit
                    }
                }
            }
            is YamlScalar -> sb.append(scalarText(n.content)).append("\n")
            is YamlNull -> sb.append("null\n")
            else -> Unit
        }
    }

    private fun unwrap(node: YamlNode): YamlNode = if (node is YamlTaggedNode) unwrap(node.innerNode) else node

    private fun scalarTextOrNull(node: YamlNode): String = when (node) {
        is YamlScalar -> scalarText(node.content)
        is YamlNull -> "null"
        else -> ""
    }

    /**
     * A scalar is emitted plain only when YAML would read it back as the
     * same value. Canonical integers/decimals and the booleans
     * `true`/`false` stay plain (config fields are typed — a quoted
     * `"18210"` would not unmarshal into an int field). Anything else
     * ambiguous — nulls, YAML 1.1 bool spellings, leading-zero numbers,
     * timestamps, reserved indicators, embedded `: ` / ` #`, edge
     * whitespace — is double-quoted so it re-parses as the same string.
     *
     * Known casualty class (documented in MihomoConfigEditor): a user
     * string that is exactly a canonical number or `true`/`false` in a
     * string-typed field loses its quotes, because kaml's node tree does
     * not retain the source scalar style.
     */
    internal fun scalarText(content: String): String {
        if (content.isEmpty()) return "\"\""
        if (needsQuoting(content)) return quote(content)
        return content
    }

    private val canonicalNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    private fun needsQuoting(s: String): Boolean {
        if (s != s.trim()) return true
        if (s.any { it == '\n' || it == '\r' || it == '\t' }) return true
        if (s.contains(": ") || s.endsWith(":") || s.contains(" #")) return true
        val first = s.first()
        if (first in "-?:,[]{}#&*!|>'\"%@`") {
            // '-' '?' ':' are only indicators when followed by space/EOL,
            // but quoting them unconditionally is always safe.
            return true
        }
        if (s.any { it in "{}[],&*#!|>'\"%@" }) return true
        if (s == "true" || s == "false") return false
        if (canonicalNumber.matches(s)) return false
        val lower = s.lowercase()
        if (lower in setOf("null", "~", "true", "false", "yes", "no", "on", "off", ".nan", ".inf", "-.inf", "+.inf")) return true
        if (looksNonCanonicalNumeric(s)) return true
        return false
    }

    /** Digit strings that are NOT canonical numbers (leading zeros, hex,
     * timestamps): quoted so they survive as strings. */
    private fun looksNonCanonicalNumeric(s: String): Boolean {
        val t = s.removePrefix("+").removePrefix("-")
        if (t.isEmpty()) return false
        if (t.all { it.isDigit() }) return true // leading-zero ints reach here
        if (t.startsWith("0x") || t.startsWith("0o")) return true
        if (s.length >= 8 && s[4] == '-' && s[7] == '-' && s.take(4).all { it.isDigit() }) return true
        return false
    }

    private fun quote(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
