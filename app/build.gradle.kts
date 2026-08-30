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
        // Android 11. Anything below 33 only works because core library
        // desugaring is enabled below - NewPipe Extractor calls Java 10/11
        // methods (URLEncoder.encode(String, Charset) and friends) that the
        // platform did not gain until API 33.
        minSdk = 30
        targetSdk = 36
        versionCode = 25
        versionName = "4.7"
        manifestPlaceholders["appLabel"] = "@string/app_name"

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
        debug {
            // Install beside the release app instead of replacing its data,
            // widgets, media session and sign-in state during testing.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Koda Debug"
        }
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
        // Load-bearing below API 33, not a nice-to-have. NewPipe Extractor
        // compiles against Java 10/11 library APIs that Android only shipped in
        // API 33 - java.net.URLEncoder.encode(String, Charset),
        // URLDecoder.decode(String, Charset) and
        // Collectors.toUnmodifiableList(). D8's built-in backports do not cover
        // those three, so without this every search throws NoSuchMethodError.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // android.util.Log is a stub in JVM unit tests and throws
            // "not mocked" on every call, so anything that logs through KLog -
            // which is most of data/ - could not be unit tested at all. Return
            // defaults instead of throwing.
            isReturnDefaultValues = true
        }
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
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // Torrent playback for TV mode sources that carry only an infoHash.
    // Only the two ABIs the splits above build for; adding x86 would put 6 MB
    // of native code in the universal APK for emulators alone.
    implementation(libs.libtorrent4j)
    implementation(libs.libtorrent4j.android.arm64)
    implementation(libs.libtorrent4j.android.arm)
    // The emulator's ABI, and debug-only so it never reaches a release APK -
    // the same reason testImplementation and debugImplementation are absent
    // from the release archive.
    debugImplementation(libs.libtorrent4j.android.x64)

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
    // DefaultMediaSourceFactory loads DashMediaSource / HlsMediaSource
    // reflectively and throws "Module missing for content type" without these.
    // Load-bearing: the NewPipe stream fallback returns a DASH manifest for
    // some videos and an HLS URL for every live stream, so a video player
    // without them dead-ends on "Source error" for exactly those.
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    // Chromecast: CastPlayer is a Player facade over a Chromecast session, so
    // video mode's transport controls drive either the local ExoPlayer or the
    // receiver through the same interface.
    implementation(libs.androidx.media3.cast)
    // Route discovery for the in-app cast device sheet.
    implementation(libs.androidx.mediarouter)
    // Core Cast classes (RemoteMediaClient, MediaStatus). media3-cast only
    // brings the -framework artifact, which is not enough on its own.
    implementation(libs.play.services.cast)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    // The home screen widget. Compose-shaped (Glance), which is why the
    // widget can share the app's design language without RemoteViews XML.
    implementation(libs.androidx.glance.appwidget)
    // Bridges a Material 3 ColorScheme into Glance, so the widgets follow the
    // palette chosen in Settings instead of raw system dynamic color.
    implementation(libs.androidx.glance.material3)
    // The background upload check over the local subscriptions feed.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.palette.ktx)

    // YouTube Music Integration
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.guava)
    // Local files may carry lyrics in ID3, Vorbis/FLAC, MP4 and other tag
    // formats. Keep that container-specific parsing out of the player.
    implementation(libs.jaudiotagger)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
