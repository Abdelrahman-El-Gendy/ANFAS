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

## Classes

- **A `GymClass` is a recurring weekly slot, not a dated occurrence.** Both designed screens agree:
  `class-schedule` asks "what's on today" and `weekly-class-schedule` repeats a class across days
  with no notion of *which* week. Storing dated instances would mean generating rows forward
  forever and deciding how far. The cost, stated rather than hidden: there is no way to cancel a
  single date or skip a public holiday — that needs an exceptions table, added when someone asks.
- **`instructorStaffId` has no foreign key, and for a different reason than `check_ins` has none.**
  A check-in copies the name because history must never change after the fact. A class slot
  should follow a live rename — the repository resolves the name at read time via `Timetable`, and
  an id that no longer resolves reads as "unassigned", which is a real, actionable state rather
  than an error.
- **A room clash is a warning, saved anyway — never a rejection.** Two classes can legitimately
  share a room (a small group in the corner), and refusing the save would make the timetable
  impossible to enter for a gym that does that. `SaveClassOutcome.SavedWithRoomClash` names the
  other class; the row is written either way. The clash check reads back **after** the write and
  excludes the row's own id, so editing a class never reports it clashing with its previous
  version of itself.
- **The overlap-lane layout algorithm is where a bug hides a class entirely, not where it looks
  wrong.** A block assigned the wrong lane draws on top of another block and the covered one
  simply disappears. `ClassSchedule.layoutDay` groups transitively (A–B–C is one group even
  though A and C don't touch, because B's width must be decided against everything it competes
  with) and assigns lanes greedily so a finished class's lane can be reused. Tested against the
  actual invariant — overlapping blocks never share a lane, one group agrees on lane count — not
  against a specific lane number, which would be as fragile as the bug itself.
- **Viewing the timetable needs no permission beyond signing in; changing it needs
  `MANAGE_CLASSES`.** Every role that can sign in is working a shift and needs to know what's on
  next, same reasoning as check-in. The split is enforced inside the screen, the same shape as
  scan/import on intake — one route, two permissions.
- **The breakpoint that switches day-list to week-grid is derived from the grid's own content
  width, never borrowed from `AnfasBreakpoints.tabletMax`.** That constant decides rail-vs-bottom-
  bar for the whole shell and is measured against the *window* — a screen hosted inside the rail
  layout only ever receives window-minus-256dp, so reusing it left the grid unreachable on an
  iPad. `GRID_MIN_WIDTH` is the hour axis plus seven legible columns, compared against the local
  `BoxWithConstraints` width instead.
- **Classes is the export's own fourth bottom-bar item** (`Dashboard`/`Members`/`Schedule`/
  `Check-in`), so it took the bar slot Reminders held; Reminders moved to `Placement.WideOnly` —
  still one tap away from the dashboard's "needs renewal" tile, which is where you'd look for it.

## Check-in

- **`CheckInPolicy` decides the outcome, never the caller.** `recordAttempt` evaluates it against
  the member's status and current term; a UI that could pass "granted" would eventually let an
  expired member in by sending the wrong flag.
- **Status outranks dates, and the two can disagree.** A member left marked ACTIVE whose term
  lapsed last month is still refused — otherwise the gym gives away the renewal it is selling. A
  term that has *not started* grants entry: they have paid, and turning them away because their
  plan begins on Monday is not defensible at the desk.
- **Refused attempts are recorded and do not move `lastCheckInAt`.** The moment someone was turned
  away is what staff get asked about later; counting it as a visit would make the directory claim
  an expired member trained today.
- **`check_ins` copies the name and number rather than joining**, and has no foreign key: deleting
  a member must not cascade away the record of them having been here, and a join would rewrite
  history after a rename.
- **The day's bounds are computed, not stored.** "Today" depends on the device zone, so a stored
  date column would let yesterday evening reappear in today's log.
- An unrecognised stored outcome reads as a **denial** — never claim someone was let in.
- Not built: **capacity**. Knowing who is *inside* needs check-out, and there is none; a percentage
  from entries alone would climb all day and read as a full gym by closing time.

## Therapy

- **No roster screen, on purpose.** The canonical desktop rail lists "Recovery," but the export
  only ever drew one therapy screen — a single patient's case file — never a caseload list. The
  entry point is the member's own profile, the same pattern as Renewal: a "Therapy" button, hidden
  rather than disabled for a session without `VIEW_THERAPY`, pushes straight to the case file for
  that member. Building a roster the export never drew would be inventing screen structure, not
  implementing the design.
- **One permission, not two.** The export's own banner reads "therapist and owner access only" as
  a single bucket, so `VIEW_THERAPY` gates viewing *and* editing, logging a session, and closing
  the case. This is the opposite call from Classes' `MANAGE_CLASSES`/view split — that split exists
  because *everyone on shift* needs to see the timetable but only some may move a class; nothing
  in the therapy design asks for a coach-can-view tier, so one permission is what the design says.
- **A case CASCADEs from its member — the opposite FK shape from `check_ins`, for a
  different reason than the shape looks similar.** A check-in has no FK because a visit's history
  must survive a deleted member: it answers "did this happen." A therapy case CASCADEs because
  clinical narrative with no member to attach to isn't a record worth keeping: it answers "is this
  still relevant." Sessions CASCADE from their case the same way `intake_rows` CASCADE from
  `intake_batches`.
- **Only one *open* case per member at a time**, enforced by the repository refusing a second
  `openCase` while one is `ACTIVE` (`SaveCaseOutcome.AlreadyOpen`) — never by the UI. A closed case
  is history, not a lock: a new case may always be opened once the old one is closed, and closing
  never deletes anything.
- **`therapistStaffId` carries no foreign key**, resolved against `staff` at read time the same way
  `GymClass.instructorStaffId` is. An id that stops resolving reads as "unassigned" — a real,
  actionable state — not an error.
- **Not built, because nothing backs it:** the export's "Next Appointment" card (there is no
  booking system — `:feature:classes` schedules recurring weekly slots, not per-patient
  appointments, and check-in is walk-in), "Files" (nothing in this app stores general attachments;
  `:core:ocr`'s capture is narrow and single-purpose), and the range-of-motion figure ("Shoulder
  Flexion 115° → 158°" is condition-specific and needs a general named-metric system to do
  honestly). Pain score is the one measurement every case can report the same way, so it is the
  one built — plotted as a real `Canvas` sparkline from actually-recorded scores, never a
  fabricated visual.
- **A "before → after" trend must not be phrased with a directional arrow in translatable text.**
  `"$first → $latest"` reads backwards inside an Arabic RTL paragraph. Phrased as real words
  instead — "from $first to $latest" / "من $first إلى $latest" — which follows each language's own
  grammar and sidesteps the direction question entirely, the text equivalent of an autoMirror icon.

## Navigation

**`TopLevel.placement` is the whole model.** The rail and the bar carry deliberately different
lists, and the export does the same — its mobile bottom bar has exactly four items on every mobile
screen, while its desktop sidebar has eight. The reason is width: a 256dp rail costs nothing per
row, whereas the bar divides a phone equally between its items.

| `Placement` | Where | Today |
|---|---|---|
| `Primary` | bar **and** rail | Dashboard, Members, Check-in, Reminders |
| `WideOnly` | rail only; on a phone, reached from its parent screen | Intake |
| `Account` | never a destination row — rail footer group, compact overflow | Staff |

- **At most four `Primary`, and that is a ceiling rather than a preference.** Material caps a bottom
  bar at five and iOS at five-plus-More. There were six here, which on a 448dp screen in Arabic
  ellipsised every label — a row of stubs whose only job was to say which is which. A fifth needs a
  real argument, and `NavigationPermissionTest` fails until someone makes it. Note the export's
  fourth is **Schedule**; ours is Reminders only because `:feature:classes` is a stub, so Classes is
  a *designed* bar destination and ranks above the other stubs.
- **Cut the bar by asking which entries are *places*.** Three kinds of thing get conflated into
  tabs: destinations; **screen actions** — "scan a sheet" belongs to the directory, since intake is
  a task whose product is members, and `members-empty` says so; and **account-level things** —
  language, staff, sign out.
- **`bottomBarSelection`, not `topLevel`, is what lights the bar.** `topLevel` returns the canonical
  destination so the rail lights itself; the bar has no slot for the other two, so intake folds onto
  Members (where a phone entered it from) and staff lights nothing.
- **A screen reachable both by push and by rail selection must not use a bare `pop()`.** From the
  rail the stack holds one entry, so `pop()` silently does nothing and the back button is dead. Use
  `popOrGoTo(fallback)`.
- **Moving an entry point is not deleting a route.** The `Config`, its `@SerialName` and its
  `requiredPermission` all stay. And check the permission *pair* when an action lands on a screen:
  SCAN_INTAKE is exercisable from the directory on a phone, so every role holding it must also hold
  VIEW_MEMBERS, or it is a permission with no way to use it.
- **A pushed screen carries its title in `AnfasDetailTopBar`, not also in `AnfasScreenHeader`.**
  The same title twice down one screen reads as two screens stacked. Desktop has no system back
  gesture, so a pushed screen without that bar cannot be left at all.
- **Chrome edges are `AnfasEdgeDivider` (10%), table rows are `AnfasTableDivider` (5%).** Two
  different tokens in the export; they are not interchangeable.

## Who is signed in

- **`AuthRepository.observeCurrentStaff()` joins the session id against `staff`** rather than the
  display name being a field on `Session`. Two reasons: the session is persisted in `Settings`,
  which is unencrypted everywhere, and a name is staff PII with no business being there when the id
  alone restores the session; and a stored copy goes stale when the Owner renames an account.
- **Avatars are initials, everywhere.** The export fills every one with a generated photograph and
  nothing in the app uploads one — for staff or members. `AnfasAvatar` takes `initials: String`, so
  `:core:designsystem` stays domain-free and one circle serves both.
- `Role.label(AppStrings)` is public in `:feature:auth`, not in `:core:i18n` — i18n owns the
  strings, but which `Role` each belongs to is that feature's business, and i18n must not start
  depending on `:core:auth`.

## Dashboards

- **The reception dashboard exists; `staff-dashboard` does not, deliberately.** Its capacity load,
  equipment issues, cleanliness timer and upcoming classes are backed by nothing — four fabricated
  figures is not a screen worth shipping. A dashboard is the screen where an invented number is
  most likely to be believed and acted on.
- **Not built on the reception dashboard either:** "Today's check-ins" (nothing records a
  check-in; `Member.lastCheckInAt` is one timestamp per member and cannot answer "how many since
  06:00"), percentage deltas (no history is kept, only current state), and the per-row SEND action
  (the WhatsApp job is parked).
- **One failing seam fails the whole screen.** A dashboard that silently omits a tile is worse
  than one saying it could not load: the missing number reads as zero, and zero here means
  "nothing to chase".
- **`RenewalQueue` in `:core:model` owns the definition of "expiring soon"**, shared with the
  profile pill via `TermProgress.EXPIRING_SOON_DAYS`. Two definitions of *soon* is how a dashboard
  ends up disagreeing with the member it links to. Expired terms are **included** and sorted
  first — a lapsed membership is money already lost, and a queue that hides it never gets chased.
- Room note: a subquery alias must not be called `inner`. Room's query verifier reads it as the
  start of an `INNER JOIN`.

## Staff sign-in

- **There is no auth server.** Accounts are local rows in `staff`, created on the device by
  whoever holds `Role.Owner`. So the interesting states are "no accounts yet" and "signed out",
  never "token expired".
- **A migration must never invent an account.** An app that ships with a default password ships
  with a published one. An install arriving at schema v6 has zero staff rows, and
  `SignInComponent` offers first-run setup for exactly that — asserted in `MigrationFromV4Test`.
- **Passwords are PBKDF2-HMAC-SHA256** at `PasswordHash.DEFAULT_ITERATIONS`, via `javax.crypto`
  on Android/JVM and CommonCrypto on Apple — both ship with the platform, so there is no crypto
  dependency and nothing hand-rolled. `PasswordHashTest` pins a **known vector** and runs on every
  target: if the two implementations diverge, an account created on the iPad cannot sign in on the
  phone. The stored `iterations` is what verifies, so raising the default never locks anyone out.
- **`SignInResult.InvalidCredentials` covers both wrong-user and wrong-password**, and the
  repository hashes against a decoy when the username is unknown so the two also *cost* the same.
  Distinguishing them lets whoever holds the device enumerate staff.
- **`SettingsSessionStore` holds no secret** — a staff id and role names. `Settings` is unencrypted
  on every platform, so a token there would be readable on a rooted device.
- **`Settings` is registered once, in `:core:common`.** Both `:core:i18n` (language) and
  `:core:data` (session) resolve it; two `single<Settings>` registrations is a Koin duplicate.
- Not built, deliberately: **"Forgot password?"** needs a server round trip, and **"Remember me"**
  would change nothing since the session persists either way.

## Cross-cutting state screens

The export's four interrupt states live in `:core:designsystem` as parameterised components, not
as screens — none belongs to a feature, because any screen can be interrupted by them.

| Screen | What backs it |
|---|---|
| `permission-denied` | **Live** for iOS camera denial via `CameraPermissions`. The design's own copy is about a staff *role*, so `States.permissionDenied*` and `Intake.cameraDenied*` are separate string sets — telling someone to ask the gym owner about an OS toggle they control is useless advice. |
| `session-expired` | Strings only. It is `AnfasEmptyState` plus copy; `staff-login` will wire it. |
| `sync-conflict` | `AnfasConflictRow`/`AnfasConflictHeader` plus `MemberConflict` in `:core:model` (tested). No sync exists, so no caller. |
| `offline-banner` | `AnfasBanner`. **Not wired, deliberately** — the export says "changes will sync when you reconnect" and nothing syncs, so that banner would be a promise the app cannot keep. |

- **Don't wrap `AnfasEmptyState` to make a "screen".** Two of the four are icon + title + message +
  action, which it already is. A named wrapper adds a file and no behaviour.
- **A platform `expect fun` is untestable by construction.** `requestCameraAccess()` could never
  have its Denied branch exercised, which is the only branch the denial UI exists for. Callers take
  the `CameraPermissions` interface instead — same reason `AppDispatchers` exists.

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
