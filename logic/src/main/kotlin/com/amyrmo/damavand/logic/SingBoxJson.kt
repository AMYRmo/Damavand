package com.amyrmo.damavand.logic

// Trimmed port of v1's domain.config.SingBoxJson: the generator layer only
// needs parse/encode (the shape/sniffing half of the v1 file lives in :model's
// import path, SingBoxLinks). Behaviour identical: lenient JSON over
// Jsonc-stripped text, pretty-printed encode.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object SingBoxJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prettyJson = Json { prettyPrint = true }

    fun parse(text: String): JsonElement? =
        runCatching { json.parseToJsonElement(Jsonc.strip(text)) }.getOrNull()

    fun encode(obj: JsonObject): String = prettyJson.encodeToString(JsonObject.serializer(), obj)
}
