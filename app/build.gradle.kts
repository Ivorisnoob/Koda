import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    val iosTargets = listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    )
    val macosTargets = listOf(
        macosX64(),
        macosArm64()
    )
    val xcf = XCFramework("shared")

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
            xcf.add(this)
        }
    }

    macosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.activity.compose)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.ui.graphics)
            implementation(libs.androidx.compose.ui.tooling.preview)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.session)
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.compose.material.icons.extended)
            implementation(libs.coil.compose)
            implementation(libs.androidx.graphics.shapes)
            implementation(libs.androidx.ui.text.google.fonts)
            implementation("androidx.palette:palette-ktx:1.0.0")

            // YouTube Music Integration
            implementation(libs.newpipe.extractor)
            implementation(libs.okhttp)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.security.crypto)
            implementation(libs.kotlinx.coroutines.guava)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    lint {
        abortOnError = false
    }
    namespace = "com.ivor.ivormusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ivor.ivormusic"
        minSdk = 31
        targetSdk = 36
        versionCode = 13
        versionName = "2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    //Only for Release apk that is why I hardcoded cause if you want to make as a maintainer release apk you can change here in future we will make it the correct way
    signingConfigs {
        create("release") {
            storeFile = file("${project.rootDir}/keystore/ivormusic.jks")
            storePassword = "password"
            keyAlias = "key0"
            keyPassword = "password"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

// Build info available via BuildConfig
android.defaultConfig.apply {
    buildConfigField("String", "GITHUB_REPO", "\"ivorisnoob/Koda\"")
    buildConfigField("String", "GITHUB_USERNAME", "\"ivorisnoob\"")
}

compose.desktop {
    application {
        mainClass = "com.ivor.ivormusic.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "IvorMusic"
            packageVersion = "2.5.0"
        }
    }
}
