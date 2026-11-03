package com.amyrmo.damavand.logic

/**
 * Only [looksLikeAddress] is ported from v1's RoutingMigration: the Xray
 * config builder's custom-rule splitting uses it to partition raw rule text
 * into IP vs domain entries. The rest of v1's RoutingMigration operates on
 * the v1 AppSettings model (ruleset seeding/migration) — a settings concern
 * for the :app layer, not for the pure generator layer.
 */
object RoutingMigration {
    fun looksLikeAddress(entry: String): Boolean {
        if (entry.startsWith("geoip:")) return true
        if (entry.startsWith("geosite:") || entry.startsWith("domain:")) return false
        val host = entry.substringBefore('/')
        val v4 = host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' }
        val v6 = host.contains(':') && host.all { it.isDigit() || it == ':' || it in 'a'..'f' || it in 'A'..'F' }
        return v4 || v6
    }
}
