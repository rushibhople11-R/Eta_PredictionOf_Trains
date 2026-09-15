import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "RailAndaz"
            packageVersion = "1.0.0"
            description = "RAILअंदाज - Real-Time AI Train Intelligence"
            copyright = "© 2026 RAILअंदाज"
            vendor = "RailAndaz"

            windows {
                perUserInstall = true
                dirChooser = true
                menuGroup = "RAILअंदाज"
                shortcut = true
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}