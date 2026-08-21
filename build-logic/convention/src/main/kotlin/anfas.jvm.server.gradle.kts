import com.anfas.buildlogic.bundle
import com.anfas.buildlogic.intVersion
import com.anfas.buildlogic.lib
import com.anfas.buildlogic.libs

/**
 * Server-only JVM config. Deliberately NOT a KMP module and deliberately without any
 * Compose plugin — the layering check enforces that :server sees only :core:model.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
    id("anfas.layering")
    id("anfas.quality")
}

kotlin {
    jvmToolchain(libs.intVersion("jvmToolchain"))
}

dependencies {
    implementation(libs.bundle("ktor-server"))
    implementation(libs.lib("logback"))
    testImplementation(libs.lib("ktor-server-testHost"))
    testImplementation(libs.bundle("test-jvm"))
}
