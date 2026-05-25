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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Koda"
            packageVersion = "3.0.0"

            macOS {
                bundleID = "com.ivor.ivormusic"
                dmgPackageVersion = "3.0.0"
            }
            windows {
                msiPackageVersion = "3.0.0"
                exePackageVersion = "3.0.0"
            }
        }
    }
}
