plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.anfas.app"
version = "1.0.0"
application {
    mainClass = "com.anfas.app.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.kotlin.testJunit)
}