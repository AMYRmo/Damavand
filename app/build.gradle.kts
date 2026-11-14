plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.amyrmo.damavand.lite"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.amyrmo.damavand.lite"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.property("damavand.versionCode") as String).toInt()
        versionName = project.property("damavand.versionName") as String
        vectorDrawables { useSupportLibrary = true }
    }

    // The lite branch still carries the old monolith tree (shared/, old
    // app/src/main/). The fresh Lite sources live under app/src/lite/ so
    // the old tree is never compiled; jniLibs still come from the CI's
    // fetch location (app/src/main/jniLibs).
    sourceSets {
        getByName("main") {
            java.srcDirs("src/lite/java")
            res.srcDirs("src/lite/res")
            manifest.srcFile("src/lite/AndroidManifest.xml")
            assets.srcDirs("src/lite/assets")
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    buildFeatures {
        // SettingsScreen reads BuildConfig.VERSION_NAME / VERSION_CODE.
        buildConfig = true
    }

    buildTypes {
        release {
            // 2.0.0: R8/minify deliberately OFF. The 1.1.x builds (AGP 9 +
            // R8 full mode) died on launch twice with no proven cause, so
            // every unproven build variable is removed until the app is
            // proven to open on-device. The APK is native-lib dominated
            // anyway; minify saves ~1 MB of dex at most.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Keep the unused core .so files (fetched by tools/get-cores.sh so the
    // CI verify step passes) OUT of the APK: Lite ships only the Xray core
    // (libgojni, from zedcore.aar) and the hev tun bridge.
    // useLegacyPackaging = true stores native libs uncompressed AND the
    // manifest sets android:extractNativeLibs="true" (the Khazar-proven
    // combination) so the JNI loader can always find them.
    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf(
                "lib/arm64-v8a/libtor.so",
                "lib/arm64-v8a/libobfs4proxy.so",
                "lib/arm64-v8a/libsnowflake.so",
                "lib/arm64-v8a/libconjure.so",
                "lib/arm64-v8a/libsnispoof.so",
                "lib/arm64-v8a/libzeptun-jni.so",
                "lib/arm64-v8a/libzeptun.so",
                "lib/arm64-v8a/libpsiphon-tunnel-core.so",
                "lib/arm64-v8a/liblyrebird.so",
                "lib/armeabi-v7a/libtor.so",
                "lib/armeabi-v7a/libobfs4proxy.so",
                "lib/armeabi-v7a/libsnowflake.so",
                "lib/armeabi-v7a/libconjure.so",
                "lib/armeabi-v7a/libsnispoof.so",
                "lib/armeabi-v7a/libzeptun-jni.so",
                "lib/armeabi-v7a/libzeptun.so",
                "lib/armeabi-v7a/libpsiphon-tunnel-core.so",
                "lib/armeabi-v7a/liblyrebird.so",
                "lib/x86_64/libtor.so",
                "lib/x86_64/libobfs4proxy.so",
                "lib/x86_64/libsnowflake.so",
                "lib/x86_64/libconjure.so",
                "lib/x86_64/libsnispoof.so",
                "lib/x86_64/libzeptun-jni.so",
                "lib/x86_64/libzeptun.so",
                "lib/x86_64/libpsiphon-tunnel-core.so",
                "lib/x86_64/liblyrebird.so",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")
    implementation(files("libs/zedcore.aar"))
}
