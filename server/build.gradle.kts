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

    // The box's own durable store. A plain JDBC driver and hand-written SQL: the server holds
    // opaque payloads and four columns of metadata, so an ORM would be more machinery than data.
    implementation(libs.sqlite.jdbc)

    // Route tests drive the app through its own wire types, so they need the client-side JSON
    // plugin; the convention plugin only supplies the server half.
    testImplementation(libs.ktor.client.contentNegotiation)
}
