// Damavand v2 — root build file. Plugin versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Guard: keep kotlin-stdlib aligned with the Kotlin compiler version from the
// catalog. Libraries built with newer Kotlin (e.g. Haze 2.1) otherwise drag in
// a stdlib whose metadata the compiler cannot read, which is what broke the
// :app compile in CI run 41613463403.
subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
        }
    }
}
