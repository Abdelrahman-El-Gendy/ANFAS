plugins {
    id("anfas.kmp.compose")
}

// Theme, tokens and shared components only. Depends on no other module — the design
// system must never need to know about the domain.

kotlin {
    sourceSets {
        /**
         * Compose layout tests, on the JVM target only.
         *
         * Deliberately not in `commonTest`: these assert *layout geometry*, which is decided by
         * common Compose code and is identical on every target, so running them three times buys
         * nothing and would drag a Skiko renderer into the iOS test binary. The JVM run is the
         * cheap one and it goes green inside `./gradlew check` with no emulator or simulator.
         *
         * `compose.desktop.currentOs` is required, not optional: these tests really compose and
         * measure, so they need Skiko's native renderer for this machine. Without it the tests
         * fail at class-load with a Skiko link error rather than an assertion.
         */
        jvmTest.dependencies {
            implementation(libs.bundles.test.composeUi)
            implementation(compose.desktop.currentOs)
        }
    }
}
