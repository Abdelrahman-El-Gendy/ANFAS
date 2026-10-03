plugins {
    id("anfas.jvm.server")
}

group = "com.anfas.app"
version = "1.0.0"

application {
    mainClass = "com.anfas.app.ApplicationKt"
}

dependencies {
    // :core:model and nothing else. The layering check enforces this — in particular it
    // keeps :core:designsystem and :core:database, and therefore Compose and Room, off
    // the server classpath.
    implementation(project(":core:model"))

    // The relay talks to Meta's Graph API with a Ktor client. The engine is CIO and is named
    // explicitly, as in :core:network, rather than discovered. These are external libraries:
    // the layering rule restricts which *projects* :server may depend on, not what it may use.
    implementation(libs.bundles.ktor.client)
    implementation(libs.ktor.client.cio)

    testImplementation(libs.ktor.client.mock)
}
