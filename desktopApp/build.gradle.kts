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
                implementation(compose.material3)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.java)
                implementation(libs.multiplatform.settings)
                implementation("com.russhwolf:multiplatform-settings-jvm:1.2.0")
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.ivor.ivormusic.MainKt"

        jvmArgs += listOf(
            // SOFTWARE rendering works on every GPU/VM without native driver requirements
            "-Dskiko.renderApi=SOFTWARE",
            // Increase stack size for deep UI trees
            "-Xss8m",
            // Encoding
            "-Dfile.encoding=UTF-8",
            // Suppress illegal-access warnings on JDK 17
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Koda"
            packageVersion = "3.0.0"
            vendor = "Ivor"
            description = "Koda Music Player"

            // Include all JVM modules so nothing is stripped by jlink
            includeAllModules = true

            macOS {
                bundleID = "com.ivor.ivormusic"
                dmgPackageVersion = "3.0.0"
            }
            windows {
                msiPackageVersion = "3.0.0"
                exePackageVersion = "3.0.0"
                upgradeUuid = "2B4F8E1A-9C3D-4F7B-A2E5-6D8C1F0B3E9A"
                menuGroup = "Koda"
                dirChooser = true
                perUserInstall = true
                shortcut = true
                console = false
            }
            linux {
                debMaintainer = "harshnandha63@gmail.com"
            }
        }
    }
}
