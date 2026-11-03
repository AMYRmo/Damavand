package com.amyrmo.damavand.model

import kotlinx.serialization.json.Json

/**
 * The canonical JSON configuration for the profile store (architecture
 * §3.3): the sealed [Profile] hierarchy serialised with its `@SerialName`
 * discriminators IS the disk format. One shared instance so the app,
 * migration and tests all read/write byte-identical shapes.
 */
object ProfileJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
}
