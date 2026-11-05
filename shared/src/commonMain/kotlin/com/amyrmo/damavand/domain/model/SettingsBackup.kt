package com.amyrmo.damavand.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object SettingsBackup {
    const val FILE_NAME = "damavand-settings.json"
    private const val MARK = "damavandSettings"
    private const val VERSION = 2

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
    }

    fun export(settings: AppSettings): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put(MARK, VERSION)
            put("settings", json.encodeToJsonElement(AppSettings.serializer(), settings))
        },
    )

    fun read(text: String): AppSettings? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val body = root["settings"]?.takeIf { root.containsKey(MARK) } ?: return null
        val decoded = json.decodeFromJsonElement(AppSettings.serializer(), body)
        val version = (root[MARK] as? JsonPrimitive)?.content?.toIntOrNull() ?: VERSION
        // One-time migration: backups written before v2 recorded the palette as
        // "Brand" for what then rendered as Damavand red. Since v2, "Brand" is
        // ZedSecure's lime again and Damavand red is its own palette, so a v1
        // "Brand" selection resolves to Damavand. v2+ backups are truthful.
        if (version < 2 && decoded.bento.palette == BentoPalette.Brand) {
            decoded.copy(bento = decoded.bento.copy(palette = BentoPalette.Damavand))
        } else {
            decoded
        }
    }.getOrNull()
}
