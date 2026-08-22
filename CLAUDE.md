# OpenWolf

@.wolf/OPENWOLF.md

This project uses OpenWolf for context management. Read and follow .wolf/OPENWOLF.md every session. Check .wolf/cerebrum.md before generating code. Check .wolf/anatomy.md before reading files.


# ANFAS — Kotlin Multiplatform

Android · iOS · Desktop (JVM) · Ktor server. Kotlin 2.4.10, AGP 9.0.1,
Compose Multiplatform 1.11.1, Gradle 9.1.0, compileSdk 36, JDK toolchain 17.

**AGP is capped by Android Studio, not by what is newest.** Studio 2026.1 supports AGP
7.1–9.2. Going above the IDE's ceiling stops Gradle sync, and a Studio that cannot sync
keeps offering run configurations for modules that no longer exist. Raise `agp` only
together with Android Studio, and remember these move as a set:

| | |
|---|---|
| `agp` | capped by the installed Android Studio |
| `gradle-wrapper` | AGP 9.0.x needs Gradle 9.1+; AGP 9.3.x needs 9.5+ |
| `android-compileSdk` | AGP 9.0.1 tops out at 36 |
| `androidx-lifecycle` | 2.11.0 needs AGP 9.1+/SDK 37, so we are on 2.10.0 |
| `androidx-core` | 1.19.0 needs AGP 9.1+/SDK 37, so we are on 1.18.0 |

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
- **Decompose components must be created on the UI thread.** On desktop that is the AWT event
  dispatch thread, not the JVM `main` thread — `desktopApp/main.kt` builds the root inside
  `SwingUtilities.invokeAndWait`. Constructing it directly on `main` throws
  `NotOnMainThreadException`.
- **`org.gradle.jvmargs` is what bounds Kotlin/Native**, because KGP runs the native compiler
  inside the Gradle daemon — not `kotlin.daemon.jvmargs`. Release framework linking for two
  iOS targets needs ~8g; the wizard's 4g OOMs. Don't lower it.
- **`:composeApp` exposes Decompose and Koin as `api`, deliberately.** Each launcher owns its
  platform lifecycle and so constructs the `RootComponent` and calls `initKoin()` itself.
- **`anfas.kmp.feature` withholds `:core:database` and `:core:network` on purpose.** Features
  go through repositories/use-cases; if a feature needs data, add the seam to `:core:*`
  rather than adding the dependency to the convention plugin.
- **AGP lint vs KSP:** AGP's lint tasks read KSP output directories without declaring a
  dependency on the producing tasks, which Gradle 9 fails on. `anfas.kmp.library` wires this
  up once — don't re-patch it per module.
- **If the IDE offers run configs for modules that don't exist** (e.g. `app.androidApp`,
  `app [hot]`), it has not re-synced. Those entries are derived from Studio's in-memory
  Gradle model, not stored in the repo, so deleting files won't clear them: run
  **File → Sync Project with Gradle Files**. `autoReloadType` in `.idea/workspace.xml` is
  set to `SELECTIVE` so this should not recur; if sync itself fails, check the AGP/Studio
  ceiling above first.
- A KMP `@Database` needs `@ConstructedBy(…)` plus an `expect object … : RoomDatabaseConstructor<…>`.
  Without it, iOS compiles fail confusingly.
- `BundledSQLiteDriver` on all three platforms so they run identical SQLite.
- Room schemas in `core/database/schemas/` are committed. Never delete them; a schema change
  means a migration.
- compose material3 is pinned to an **alpha** (`1.11.0-alpha07`) — the newest in the 1.11 line.
- KSP versioning is independent of Kotlin's. There is no `2.4.10-x.y.z`.

## RTL and bidi

- **`dataMonoLtr` is for Latin-only runs** — phone numbers, ids, raw template names. Never put a
  *localised* string through it: a translated month name or the `ج.م` currency abbreviation gets
  its runs reordered, which rendered `22 أغسطس 2026` as `22 2026 أغسطس`.
- **Wrap the number, not the sentence.** For a localised string containing a Latin/numeric run,
  use `String.asLtrIsolate()` (LRI/PDI) on the run and let the `Text` follow the paragraph.
  Direction-**neutral** characters (`#`, `+`, `-`, currency codes) otherwise resolve from their
  RTL surroundings and migrate to the wrong end — `#10003` read as `10003#`.
- Verify in Arabic on a device. Neither of the above fails a test or looks wrong in English.

## Release configuration

- **Every navigation `Config` variant carries an explicit `@SerialName`.** Decompose serialises
  the back stack into Essenty's `StateKeeper`, and the default discriminator is the class name,
  which R8 is free to rename. Within one build writer and reader agree, so it tests clean; it
  breaks when a build reads state saved by a build that obfuscated differently -- a crash on cold
  resume after an app update, production only. `ConfigSerializationTest` locks the strings, and
  they are **append-only**: an old value may be sitting in saved state on someone's phone.
- **R8 is on for `release` and `stage`.** `stage` is minified *and* debuggable, which is the only
  way to run instrumented tests against R8 output (`connectedAndroidTest` needs a debuggable APK
  and `release` must never be one). `testBuildType = "stage"`.
- **Verify R8 by installing and reading logcat, not by reasoning.** ML Kit registers components
  through Firebase's `ComponentDiscovery`, which reflectively instantiates classes named in
  manifest metadata; R8 stripped the constructors and OCR silently stopped working, as a
  *warning*. The keep rule is in `androidApp/proguard-rules.pro` with the log line that found it.
- **Signing material is never committed.** `keystore.properties` (Android) and
  `Configuration/Local.xcconfig` (iOS `TEAM_ID`) are gitignored, with a committed `.template` for
  the latter. Release falls back to debug signing when the keystore is absent so a fresh clone
  still builds -- a convenience, never a shipping path. Confirm with
  `apksigner verify --print-certs`.
- **The bundle/package ids differ on purpose:** iOS and Android are `com.anfas.app`; desktop is
  `com.anfas.app.desktop`, so an iPad build on the same Apple-silicon Mac cannot collide in
  LaunchServices. `linux.packageName` must stay lowercase -- dpkg rejects uppercase.
- **jpackage cannot cross-build**, so `targetFormats` is derived from `OperatingSystem.current()`.
  Declaring Dmg+Msi+Deb together means every host fails on two of three.
- The Room database is excluded from cloud backup and device transfer: the whole domain is PII.

## Commands

```
./gradlew build                                    everything
./gradlew check                                    tests + layering rules
./gradlew :androidApp:assembleDebug                Android
./gradlew :composeApp:compileKotlinIosSimulatorArm64   iOS compile check
./gradlew :desktopApp:run                          desktop
./gradlew :server:run                              server → GET /health
```

iOS runs from Xcode: open `app/iosApp/iosApp.xcodeproj`. Its build phase invokes
`:composeApp:embedAndSignAppleFrameworkForXcode`, and `ContentView.swift` hosts
`MainViewController()` from the `ComposeApp` framework.

## Adding a module

1. `include(":feature:foo")` in `settings.gradle.kts`.
2. `feature/foo/build.gradle.kts` → `plugins { id("anfas.kmp.feature") }` and nothing else
   unless the module genuinely needs something extra.
3. Add its Koin module to `featureModules` in `composeApp/src/commonMain/.../di/Modules.kt`
   and the project to `:composeApp`'s dependencies.
4. `./gradlew check` — the layering rules run automatically.

Never add a version or a `group:artifact:version` string to a build file. Catalog only.
