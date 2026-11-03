import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :app — the only Android module (architecture §1.1/§1.3).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.amyrmo.damavand"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.amyrmo.damavand"
        minSdk = 26 // raised from v1's 24 (architecture §1.3: Haze floor)
        targetSdk = 37
        versionCode = (project.property("damavand.versionCode") as String).toInt()
        versionName = project.property("damavand.versionName") as String
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Release ships the arm64 APK only (architecture §1.3); debug keeps
    // x86_64 for emulators.
    splits {
        abi {
            isEnable = true
            reset()
            if (gradle.startParameter.taskNames.any { it.contains("Release") }) {
                include("arm64-v8a")
            } else {
                include("arm64-v8a", "x86_64")
            }
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // Mandatory: the .so files must be extracted to nativeLibraryDir
            // or the exec cores (Aether, Mihomo, CottenDNS) cannot run
            // (architecture §1.3).
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":model"))
    implementation(project(":logic"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)

    // Liquid Glass, dock only (architecture §5.3, decision D8).
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.material3)
    implementation(libs.haze.glass.material3)

    // Binary cores fetched by tools/ scripts (gitignored, see tools/cores.txt).
    implementation(files("libs/zedcore.aar"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
}
