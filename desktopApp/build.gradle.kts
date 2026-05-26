import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.ivor.ivormusic.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Koda"
            packageVersion = "3.0.0"
            vendor = "Ivor"
            description = "Koda Music Player"

            macOS {
                bundleID = "com.ivor.ivormusic"
                dmgPackageVersion = "3.0.0"
                // Set via CI env: COMPOSE_MAC_ARCH=x86_64 or arm64
                // Default builds native arch; CI overrides per job
            }
            windows {
                msiPackageVersion = "3.0.0"
                exePackageVersion = "3.0.0"
                upgradeUuid = "2B4F8E1A-9C3D-4F7B-A2E5-6D8C1F0B3E9A"
                menuGroup = "Koda"
                dirChooser = true
                perUserInstall = true
                shortcut = true
            }
        }
    }
}
