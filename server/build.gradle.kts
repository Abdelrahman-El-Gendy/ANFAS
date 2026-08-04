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
}
