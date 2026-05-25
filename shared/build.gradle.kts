import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
            )
        }
    }

    // Desktop (Windows / macOS / Linux) via JVM
    jvm("desktop")

    // iOS targets — uncomment when ready for iOS build
    // listOf(
    //     iosArm64(),
    //     iosSimulatorArm64()
    // ).forEach { iosTarget ->
    //     iosTarget.binaries.framework {
    //         baseName = "Shared"
    //         isStatic = true
    //     }
    // }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.animation)

            // Navigation (CMP multiplatform navigation)
            implementation(libs.androidx.navigation.compose)

            // Lifecycle / ViewModel (KMP-compatible since 2.9.x)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            // Ktor 3 (KMP-first)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Coil 3 (fully KMP — no Context required)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor3)

            // Multiplatform Settings (replaces SharedPreferences)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            // Kotlinx
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            // Ktor OkHttp engine on Android
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)

            // Media3 / ExoPlayer (Android only)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.ui)

            // NewPipe Extractor (Android only — JVM/OkHttp dependent)
            implementation(libs.newpipe.extractor)
            implementation(libs.kotlinx.coroutines.guava)

            // Encrypted storage (Android only)
            implementation(libs.androidx.security.crypto)

            // Palette for album art color extraction
            implementation("androidx.palette:palette-ktx:1.0.0")

            // Accompanist permissions
            implementation(libs.accompanist.permissions)

            // Core KTX
            implementation(libs.androidx.core.ktx)

            // Material 3 Expressive (latest alpha for Android)
            implementation(libs.androidx.compose.material3)
        }

        // Desktop dependencies (JVM-based)
        val desktopMain by getting {
            dependencies {
                // Ktor JVM engine for desktop
                implementation(libs.ktor.client.java)

                // OkHttp for downloads (JVM)
                implementation(libs.okhttp)

                // NewPipe Extractor (JVM — same as Android)
                implementation(libs.newpipe.extractor)
            }
        }

        // iOS dependencies — uncomment with iOS targets
        // val iosMain by getting {
        //     dependencies {
        //         implementation(libs.ktor.client.darwin)
        //     }
        // }
    }
}

android {
    namespace = "com.ivor.ivormusic.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
