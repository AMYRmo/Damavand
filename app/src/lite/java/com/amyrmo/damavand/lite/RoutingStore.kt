package com.amyrmo.damavand.lite

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Routing + per-app proxy + per-server usage, stored as plain JSON
 * files (3.1.0, PattNG). Same quiet pattern as ProfileRepository:
 * a read failure returns the default, a write failure is swallowed.
 */
class RoutingStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val routingFile: File get() = File(context.filesDir, "routing.json")
    private val perAppFile: File get() = File(context.filesDir, "perapp.json")
    private val usageFile: File get() = File(context.filesDir, "usage.json")

    // ---- routing ----

    fun loadRouting(): RoutingConfig {
        return try {
            if (!routingFile.exists()) return RoutingConfig()
            json.decodeFromString<RoutingConfig>(routingFile.readText())
        } catch (_: Exception) {
            RoutingConfig()
        }
    }

    fun saveRouting(config: RoutingConfig) {
        try {
            routingFile.writeText(json.encodeToString(config))
        } catch (_: Exception) {
        }
    }

    fun setRoutingMode(mode: String) {
        saveRouting(loadRouting().copy(mode = mode))
    }

    fun addRule(rule: RoutingRule) {
        val config = loadRouting()
        saveRouting(config.copy(rules = config.rules + rule))
    }

    fun updateRule(updated: RoutingRule) {
        val config = loadRouting()
        saveRouting(config.copy(rules = config.rules.map { if (it.id == updated.id) updated else it }))
    }

    fun deleteRule(id: String) {
        val config = loadRouting()
        saveRouting(config.copy(rules = config.rules.filter { it.id != id }))
    }

    // ---- per-app proxy ----

    fun loadPerApp(): PerAppConfig {
        return try {
            if (!perAppFile.exists()) return PerAppConfig()
            json.decodeFromString<PerAppConfig>(perAppFile.readText())
        } catch (_: Exception) {
            PerAppConfig()
        }
    }

    fun savePerApp(config: PerAppConfig) {
        try {
            perAppFile.writeText(json.encodeToString(config))
        } catch (_: Exception) {
        }
    }

    fun setPerAppMode(mode: String) {
        savePerApp(loadPerApp().copy(mode = mode))
    }

    fun togglePerAppPackage(packageName: String) {
        val config = loadPerApp()
        val pkgs = config.packages.toMutableSet()
        if (packageName in pkgs) pkgs.remove(packageName) else pkgs.add(packageName)
        savePerApp(config.copy(packages = pkgs))
    }

    // ---- per-server usage (bytes per profile id, all-time) ----

    fun loadUsage(): Map<String, Long> {
        return try {
            if (!usageFile.exists()) return emptyMap()
            json.decodeFromString<Map<String, Long>>(usageFile.readText())
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun recordUsage(profileId: String, bytes: Long) {
        if (profileId.isBlank() || bytes <= 0) return
        try {
            val usage = loadUsage().toMutableMap()
            usage[profileId] = (usage[profileId] ?: 0L) + bytes
            usageFile.writeText(json.encodeToString(usage))
        } catch (_: Exception) {
        }
    }
}
