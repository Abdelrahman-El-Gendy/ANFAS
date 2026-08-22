import com.anfas.buildlogic.appVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/**
 * Thin desktop launcher. Plain kotlin("jvm") rather than a kmp.* convention plugin, because
 * the Compose Desktop application DSL is JVM-only.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("anfas.quality")
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":composeApp"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

/**
 * jpackage cannot cross-build: Dmg needs macOS, Msi needs Windows, Deb needs Linux. Declaring all
 * three meant every host failed on two of them, so `packageDistributionForCurrentOS` was the only
 * task that could ever work and `packageDmg` on Linux failed confusingly. The format set is
 * derived from the host instead, and each CI job builds its own installer.
 */
val hostFormats: List<TargetFormat> = when {
    org.gradle.internal.os.OperatingSystem.current().isMacOsX -> listOf(TargetFormat.Dmg)
    org.gradle.internal.os.OperatingSystem.current().isWindows -> listOf(TargetFormat.Msi)
    else -> listOf(TargetFormat.Deb)
}

compose.desktop {
    application {
        mainClass = "com.anfas.app.MainKt"

        jvmArgs(
            // Skiko plus a Room/SQLite page cache needs headroom, and the default heap on a
            // jpackage image is a fraction of physical RAM — small on a 32GB machine, tiny on 8.
            "-Xmx1024m",
            // Without this macOS frames a #141311 app in a light title bar.
            "-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua",
        )

        nativeDistributions {
            targetFormats(*hostFormats.toTypedArray())

            // The user-visible application name. It was "com.anfas.app", which shipped a folder
            // literally called com.anfas.app into /Applications and produced
            // com.anfas.app-1.0.0.dmg. The reverse-DNS id belongs in macOS.bundleID below.
            packageName = "ANFAS"
            packageVersion = appVersion().get().packageVersion
            description = "Gym membership, subscriptions and paper-sheet intake"
            vendor = "ANFAS"

            macOS {
                // Deliberately NOT com.anfas.app, which is the iOS/Android id. An iPad build of
                // the same app on an Apple-silicon Mac would otherwise collide with this one in
                // LaunchServices.
                bundleID = "com.anfas.app.desktop"
                // The JVM JITs and Skiko loads unsigned dylibs, so a hardened runtime needs
                // these three or the app is killed on launch once it is signed and notarised.
                entitlementsFile.set(project.file("entitlements.plist"))
            }

            windows {
                // Generated once and immutable. If this changes, every MSI installs alongside
                // the previous version instead of upgrading it.
                upgradeUuid = "9F3A6C21-5E48-4B7D-9A0C-7D1E2B84F5C3"
                menuGroup = "ANFAS"
            }

            linux {
                // dpkg rejects uppercase Debian package names, so this cannot be "ANFAS".
                packageName = "anfas"
                debMaintainer = "noreply@anfas.app"
            }
        }
    }
}
