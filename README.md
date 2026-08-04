This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM) and a Ktor server.

See [CLAUDE.md](./CLAUDE.md) for the module graph, layering rules and build conventions.

## Structure

* [/composeApp](./composeApp/src) — shared Compose Multiplatform app shell (DI wiring,
  navigation root, platform entry points). `commonMain` is shared across all targets;
  `androidMain`, `iosMain` and `jvmMain` hold the platform-specific parts.
* [/androidApp](./androidApp/src) and [/desktopApp](./desktopApp/src) — thin launchers.
  AGP 9 no longer allows the Kotlin Multiplatform plugin in the same module as
  `com.android.application`, so each platform gets its own entry-point module.
* [/app/iosApp](./app/iosApp) — the iOS application and any SwiftUI code. Links the
  `ComposeApp` framework produced by `:composeApp`.
* [/core](./core) — `model`, `common`, `designsystem`, `database`, `network`, `auth`.
* [/feature](./feature) — one module per feature; features never depend on each other.
* [/server](./server/src/main/kotlin) — Ktor server. Plain JVM, not a KMP module.
* [/build-logic](./build-logic) — convention plugins. All shared build config lives here.

## Running the apps

- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :desktopApp:run`
- Server: `./gradlew :server:run`, then `curl localhost:8080/health`
- iOS: open [/app/iosApp](./app/iosApp) in Xcode and run from there.

## Running tests

- Everything, plus the layering rules: `./gradlew check`
- Android host tests: `./gradlew testAndroidHostTest`
- Desktop/JVM tests: `./gradlew jvmTest`
- iOS tests: `./gradlew :core:common:iosSimulatorArm64Test`
- Server tests: `./gradlew :server:test`

## iOS targets

`iosArm64` and `iosSimulatorArm64` only. `iosX64` (Intel simulator) is not available:
`androidx.room3` and `androidx.sqlite` no longer publish x64 Apple artifacts. See CLAUDE.md.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
