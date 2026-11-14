import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.amyrmo.damavand.lite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.amyrmo.damavand.lite"
        minSdk = 26
        targetSdk = 36
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
            java.directories.clear()
            java.directories.add("src/lite/java")
            res.directories.clear()
            res.directories.add("src/lite/res")
            manifest.srcFile("src/lite/AndroidManifest.xml")
            assets.directories.clear()
            assets.directories.add("src/lite/assets")
            jniLibs.directories.clear()
            jniLibs.directories.add("src/main/jniLibs")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    // Keep the unused core .so files (fetched by tools/get-cores.sh so the
    // CI verify step passes) OUT of the APK: Lite ships only the Xray core
    // (libgojni, from zedcore.aar) and the hev tun bridge.
    packaging {
        jniLibs {
            excludes += setOf(
                "lib/arm64-v8a/libtor.so",
                "lib/arm64-v8a/libobfs4proxy.so",
                "lib/arm64-v8a/libsnowflake.so",
                "lib/arm64-v8a/libconjure.so",
                "lib/arm64-v8a/libsnispoof.so",
                "lib/arm64-v8a/libzeptun-jni.so",
                "lib/arm64-v8a/libpsiphon-tunnel-core.so",
                "lib/arm64-v8a/liblyrebird.so",
                "lib/armeabi-v7a/libtor.so",
                "lib/armeabi-v7a/libobfs4proxy.so",
                "lib/armeabi-v7a/libsnowflake.so",
                "lib/armeabi-v7a/libconjure.so",
                "lib/armeabi-v7a/libsnispoof.so",
                "lib/armeabi-v7a/libzeptun-jni.so",
                "lib/armeabi-v7a/libpsiphon-tunnel-core.so",
                "lib/armeabi-v7a/liblyrebird.so",
                "lib/x86_64/libtor.so",
                "lib/x86_64/libobfs4proxy.so",
                "lib/x86_64/libsnowflake.so",
                "lib/x86_64/libconjure.so",
                "lib/x86_64/libsnispoof.so",
                "lib/x86_64/libzeptun-jni.so",
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
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.0")
    implementation("io.coil-kt.coil3:coil-svg:3.6.0")
    implementation(files("libs/zedcore.aar"))
}
