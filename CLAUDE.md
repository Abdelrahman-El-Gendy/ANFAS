# ANFAS — Kotlin Multiplatform

Android · iOS · Desktop (JVM) · Ktor server. Kotlin 2.4.10, AGP 9.3.1,
Compose Multiplatform 1.11.1, Gradle 9.6.1, compileSdk 37, JDK toolchain 17.

AGP 9.3.x requires Gradle 9.5.0+ — the wrapper and AGP versions move together.

## Module graph

```
:composeApp   shared Compose app shell — DI wiring, Decompose root, platform entry points
:androidApp   com.android.application launcher.  THIN. no logic.
:desktopApp   kotlin("jvm") + compose launcher.  THIN. no logic.
app/iosApp/   Xcode project. Links the "ComposeApp" static framework.

:core:model         pure Kotlin domain types. depends on NOTHING.
:core:common        Result types, dispatchers, validation
:core:designsystem  theme, tokens, shared components
:core:database      Room KMP (androidx.room3)
:core:network       Ktor client
:core:auth          session, RBAC

:feature:members :feature:subscriptions :feature:intake-ocr :feature:therapy
:feature:classes :feature:announcements :feature:equipment

:server       plain JVM Ktor. NOT a KMP module.
```

## Layering rules — enforced by `./gradlew check`

1. Feature modules may depend on core modules. **A feature module must never depend on
   another feature module.** Cross-feature needs go through `:core:*`.
2. `:core:model` depends on nothing — no project deps, and no external deps beyond
   kotlin-stdlib / coroutines-core / serialization-core / datetime.
3. `:server` depends on `:core:model` and nothing else. Never designsystem, database, or
   anything that would drag Compose onto the server classpath.
4. `:core:*` must never depend on `:feature:*`.
5. `:androidApp` / `:desktopApp` are launchers. All shared code lives in `:composeApp` or below.

The `anfas.layering` plugin (build-logic) fails the build on violations. Don't work around it.

## Conventions

- **Every dependency lives in `gradle/libs.versions.toml`, with bundles.** No hardcoded
  `"group:artifact:version"` in any build file, ever.
- **Shared build config lives in `build-logic/`, never copy-pasted.** Module build files apply
  a convention plugin and declare only their own dependencies:
  `anfas.kmp.library` · `anfas.kmp.compose` · `anfas.kmp.feature` · `anfas.jvm.server`.
- **Never invent a version number.** Resolve it from Maven Central / Google's Maven and pin it.
- AGP 9: the KMP plugin cannot coexist with `com.android.application`/`com.android.library` in
  one module. KMP library modules use `com.android.kotlin.multiplatform.library` and configure
  Android inside `kotlin { android { … } }`.
- iOS targets: `iosArm64`, `iosSimulatorArm64`. **Do not add `iosX64`** — see below.
- Logging is Kermit (`Logger.withTag(...)`). DI is Koin. Navigation is Decompose.

## Watch out for

- **KSP must be declared per target.** A single `ksp(...)` line silently generates nothing on
  native targets. `:core:database` declares `kspAndroid`, `kspIosArm64`,
  `kspIosSimulatorArm64`, `kspJvm` — add every new target here too.
- **Room is `androidx.room3` (3.0.1), not `androidx.room`.** Imports are `androidx.room3.*`,
  plugin id is `androidx.room3`, and the Gradle extension is `room3 { }` — not `room { }`.
  The `androidx.room` 2.x coordinates are the older Android-first line.
- **`iosX64` is not available.** androidx.room3 3.0.1 and androidx.sqlite 2.7.0 publish no x64
  Apple artifacts at all (`room3-runtime-iosx64` / `sqlite-bundled-iosx64` do not exist) —
  Google moved Apple targets to arm64-only. Adding `iosX64()` makes `:core:database`
  unresolvable. Regaining it would mean downgrading to `androidx.room` 2.8.4 + `sqlite` 2.6.2,
  which we deliberately chose not to do.
- **`Dispatchers.IO` is unusable in `commonMain`** — absent from the common source set, and
  `internal` on Kotlin/Native. Inject `AppDispatchers` from `:core:common` instead; native
  resolves `io` to the Default pool.
- A KMP `@Database` needs `@ConstructedBy(…)` plus an `expect object … : RoomDatabaseConstructor<…>`.
  Without it, iOS compiles fail confusingly.
- `BundledSQLiteDriver` on all three platforms so they run identical SQLite.
- Room schemas in `core/database/schemas/` are committed. Never delete them; a schema change
  means a migration.
- compose material3 is pinned to an **alpha** (`1.11.0-alpha07`) — the newest in the 1.11 line.
- KSP versioning is independent of Kotlin's. There is no `2.4.10-x.y.z`.

## Commands

```
./gradlew build                                    everything
./gradlew check                                    tests + layering rules
./gradlew :androidApp:assembleDebug                Android
./gradlew :composeApp:compileKotlinIosSimulatorArm64   iOS compile check
./gradlew :desktopApp:run                          desktop
./gradlew :server:run                              server → GET /health
```
