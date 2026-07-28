import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing credentials: CI supplies them as env vars, local builds read
// them from local.properties (gitignored). Env wins so CI never picks up a
// stray local file.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingCredential(envName: String, propName: String): String? =
    System.getenv(envName) ?: localProps.getProperty(propName)

android {
    namespace = "com.ivor.ivormusic"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ivor.ivormusic"
        minSdk = 30
        targetSdk = 36
        versionCode = 21
        versionName = "4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file("${project.rootDir}/keystore/ivormusic.jks")
            storePassword = signingCredential("KEYSTORE_PASSWORD", "keystore.storePassword")
            keyAlias = signingCredential("KEY_ALIAS", "keystore.keyAlias")
            keyPassword = signingCredential("KEY_PASSWORD", "keystore.keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }

}

// AGP 9 removed the android.kotlinOptions {} block; Kotlin compiler settings live here now.
// The two opt-ins are load-bearing: the M3 Expressive APIs used across the UI layer
// (MaterialShapes, LoadingIndicator) are still experimental and will not compile without them.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}

// Build info available via BuildConfig
android.defaultConfig.apply {
    buildConfigField("String", "GITHUB_REPO", "\"ivorisnoob/Koda\"")
    buildConfigField("String", "GITHUB_USERNAME", "\"ivorisnoob\"")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
