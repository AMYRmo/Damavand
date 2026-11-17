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

    // ---- per-server usage (bytes per profile id, bucketed by local day) ----
    //
    // 3.2.29: usage is stored per day ("yyyy-MM-dd" -> profileId -> bytes)
    // so the Stats tab can show Today / This week / This month. NOTHING
    // older than 30 days is kept: every load and every record prunes day
    // keys older than today-29 (30 days kept, inclusive). The old flat
    // format (profileId -> all-time bytes) is migrated once into today's
    // bucket, so existing totals are not lost — they age out after 30 days.

    companion object {
        const val USAGE_RETENTION_DAYS = 30
    }

    private fun todayKey(): String = java.time.LocalDate.now().toString()

    private fun pruneDays(days: Map<String, Map<String, Long>>): Map<String, Map<String, Long>> {
        val cutoff = java.time.LocalDate.now()
            .minusDays((USAGE_RETENTION_DAYS - 1).toLong()).toString()
        // Day keys are ISO dates, so lexicographic compare is chronological.
        return days.filterKeys { it >= cutoff }
    }

    private fun saveUsageDays(days: Map<String, Map<String, Long>>) {
        try {
            usageFile.writeText(json.encodeToString(days))
        } catch (_: Exception) {
        }
    }

    fun loadUsageDays(): Map<String, Map<String, Long>> {
        return try {
            if (!usageFile.exists()) return emptyMap()
            val text = usageFile.readText()
            val days: Map<String, Map<String, Long>> = try {
                json.decodeFromString(text)
            } catch (_: Exception) {
                // Old flat format (3.2.28 and earlier): profileId -> all-time
                // bytes. Migrate once into today's bucket.
                val flat: Map<String, Long> = json.decodeFromString(text)
                if (flat.isEmpty()) return emptyMap()
                mapOf(todayKey() to flat)
            }
            val pruned = pruneDays(days)
            if (pruned.size != days.size) saveUsageDays(pruned)
            pruned
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun recordUsage(profileId: String, bytes: Long) {
        if (profileId.isBlank() || bytes <= 0) return
        try {
            val days = loadUsageDays().toMutableMap()
            val today = (days[todayKey()] ?: emptyMap()).toMutableMap()
            today[profileId] = (today[profileId] ?: 0L) + bytes
            days[todayKey()] = today
            saveUsageDays(pruneDays(days))
        } catch (_: Exception) {
        }
    }
}
