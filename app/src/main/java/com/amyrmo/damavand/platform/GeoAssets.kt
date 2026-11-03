package com.amyrmo.damavand.platform

import android.content.Context
import java.io.File

/**
 * The Xray geo files (architecture §2.5): `Libv2ray.initCoreEnv` points at
 * `filesDir/assets`; the bundled fallback snapshot (§1.4) is copied there
 * from the APK assets when present, and the runtime updater (a later
 * phase) refreshes the same directory. A config that references geo codes
 * is planned with `geoAssetsAvailable` from [ensure] so the builder only
 * emits geo rules when the files exist.
 */
object GeoAssets {

    private val FILES = listOf("geoip.dat", "geosite.dat")

    /** The directory Libv2ray reads geo files from. */
    fun dir(context: Context): File = File(context.filesDir, "assets").apply { mkdirs() }

    /**
     * Copy any bundled geo files into place; returns true when both files
     * are present afterwards.
     */
    fun ensure(context: Context): Boolean {
        val target = dir(context)
        for (name in FILES) {
            val out = File(target, name)
            if (out.isFile && out.length() > 0) continue
            runCatching {
                context.assets.open(name).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }
        return FILES.all { File(target, it).let { f -> f.isFile && f.length() > 0 } }
    }
}
