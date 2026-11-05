package com.amyrmo.damavand

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.amyrmo.damavand.core.XrayController
import com.amyrmo.damavand.core.platform.LocaleManager
import com.amyrmo.damavand.data.settings.SettingsRepository
import com.amyrmo.damavand.di.AppContainer

class DamavandApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base, SettingsRepository.readLanguageTag(base)))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.reassert(SettingsRepository.readLanguageTag(this))
    }

    override fun onCreate() {
        super.onCreate()

        // Cap the Go cores' heap growth (Xray / sing-box / Psiphon all run
        // in-process via gomobile): without a limit the Go GC lets the heap
        // balloon well past working-set size. Must be set before the first
        // core class loads (warmUp below), while the runtime still reads env.
        runCatching { android.system.Os.setenv("GOMEMLIMIT", "192MiB", true) }

        container = AppContainer(this)

        XrayController.warmUp(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
}
