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
:core:i18n          typed AppStrings, EN/AR, CompositionLocal-driven (not Compose Resources)
:core:database      Room KMP (androidx.room3)
:core:data          repositories — the only way a feature reaches storage
:core:network       Ktor client
:core:ocr           camera capture + text recognition; declared only by :feature:intake-ocr
:core:auth          session, RBAC

:feature:auth :feature:dashboard :feature:members :feature:subscriptions
:feature:intake-ocr :feature:checkin :feature:therapy :feature:classes
:feature:announcements :feature:equipment

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

## Announcements

- **`create-announcement` is the export's own fourth bottom-bar-equivalent, and it never drew a
  mobile screen at all.** `deviceType: DESKTOP` only, no phone/tablet counterpart anywhere in the
  export — unlike every other feature so far, which had at least a phone-reachable path. That is
  what earns `Placement.DesktopOnly`: rail only, with genuinely no mobile entry point, as opposed
  to `WideOnly` (rail + reachable from a mobile parent screen), which would claim a mobile path
  that does not exist.
- **Publishing repeats the WhatsApp Reminder Queue's pattern: build the real staff-facing
  composition and audience-targeting layer even though delivery has nobody to reach.**
  `AnnouncementRepository.publish()` computes and freezes a real `recipientCountAtPublish` against
  live `members`/`current subscriptions` at the moment of publish — never a placeholder — but no
  push or WhatsApp channel exists to actually notify anyone. The number is honest; the audience
  just never sees it yet.
- **Only 3 of the export's 5 audience segments are honestly computable, and the other two are
  named in `AnnouncementAudience`'s own KDoc rather than silently omitted.** `ALL_MEMBERS`,
  `ACTIVE_ONLY`, and `EXPIRING_THIS_MONTH` (sharing `TermProgress.EXPIRING_SOON_DAYS` with the
  dashboard's renewal queue, so "soon" never disagrees with itself) are backed by real member/term
  data. "By class attendance" and "by therapy status" would need attendance history and clinical
  data joined in ways nothing in this app currently supports.
- **There is no unpublish, so the guard against re-publishing lives in the component, not just the
  dialog.** The form dialog withholds the Publish/Delete actions once `AnnouncementForm.wasPublished`
  is true, but `AnnouncementsComponent.onRequestPublish()` also refuses directly
  (`if (form.wasPublished) return`) — a component method is callable from anywhere, so hiding a
  button is UX, not enforcement. Same principle as `CheckInPolicy` deciding the outcome instead of
  the caller. A published announcement's dialog offers only Cancel and "Save changes" (a distinct
  i18n string from "Save draft" — the wording must not imply a draft still exists).
- **The list+dialog UI is a deliberate departure from the export's split editor-with-live-preview**,
  documented via KDoc rather than silently simplified: no rich text or image storage exists, no
  per-record bilingual authoring pattern exists elsewhere in the app, there is no member-facing app
  to preview into, and no background scheduler exists for a "send later" option the export implies.

## Equipment

- **`equipment-detail` is `D` (desktop) in the export's own screen inventory** (`design/stitch/
  TOKENS.md`), the same as `create-announcement` — so it takes `Placement.DesktopOnly` too, its
  second user. Checking that table settles the placement question directly rather than arguing
  it fresh per screen.
- **Viewing needs only `VIEW_MEMBERS`; `MANAGE_EQUIPMENT` gates everything else**, the same split
  as Classes and for the same reason: a coach on the floor needs to know a treadmill is broken,
  but adding inventory, logging maintenance and changing status is reception/operations work.
  This is the opposite shape from Announcements, whose *route itself* requires
  `MANAGE_ANNOUNCEMENTS` — the right shape follows from whether there is an operational reason
  for every role to see the screen, not from a blanket rule either way.
- **A maintenance-log entry is an issue report or a service record, told apart by whether
  `technician` is set, not by a separate type field.** `MaintenanceLog.lastServiceOn` filters to
  entries carrying a technician before taking the most recent `occurredAt` — matching the export's
  own "Last service" field, which tracks completed work rather than a problem someone noticed.
  A later plain report must never push that date forward; the case is pinned by a dedicated test.
- **No date field on the log-maintenance form.** Every entry stamps `occurredAt` as "now" at
  submission time — the same simplification already made for announcements' `createdAt` and for
  check-ins — because nothing here needs a person to backdate a repair, unlike a therapy session
  where the treatment date has clinical meaning and earns its own day-offset picker.
- **`logMaintenance` writes the log row and moves `Equipment.status` to the caller-supplied
  `resultingStatus` in the same call**, rather than taking two separate actions — the point of
  writing up what was done is to say what state it leaves the machine in, the same reasoning
  `CheckInPolicy` uses for deciding an outcome rather than a caller assembling one.
- **A duplicate asset tag is refused at creation**, the one piece of real validation beyond
  blank-field checks: two rows sharing a physical unit's tag would make "which treadmill" ambiguous
  the moment staff read the tag off the machine instead of the app.
- **The detail drawer is `AnfasDialog`, not a new sliding-panel primitive**, matching the list-
  plus-dialog shape Classes, Therapy and Announcements all already use for one record's detail —
  a dedicated component for the export's slide-in panel would serve no other screen.
- **`ChipTone.Warning` is a new addition to `:core:designsystem`**, for "needs service" specifically
  — neither `Positive` (actually operational) nor `Critical` (reserved for fully out of order) fit
  the export's own distinct amber badge for this status.

## Intake / OCR

- **The source pane renders the actual photo with `SubcomposeAsyncImage` (Coil 3), scaled with
  `ContentScale.FillBounds` rather than `Fit`.** `IntakeBatch` stores no per-photo aspect ratio —
  the overlay boxes are already positioned against a fixed `SHEET_ASPECT_RATIO` (A4) rect rather
  than the photo's true dimensions, a pre-existing simplification. `Fit` would letterbox the real
  image inside that rect at its own aspect ratio, pulling the rendered photo out of alignment with
  overlay boxes computed against the rect's full extent; `FillBounds` keeps both aligned to the
  same coordinate space, at the cost of a small stretch when the photo isn't quite A4 — the same
  trade the overlay boxes already made. The Coil `error` slot reuses `sourceImageNotRendered`
  (previously the only text ever shown here) for the real case where the file has gone missing
  from disk; `noSourceImage` still covers a batch with no photo at all.

## Navigation

**`TopLevel.placement` is the whole model.** The rail and the bar carry deliberately different
lists, and the export does the same — its mobile bottom bar has exactly four items on every mobile
screen, while its desktop sidebar has eight. The reason is width: a 256dp rail costs nothing per
row, whereas the bar divides a phone equally between its items.

| `Placement` | Where | Today |
|---|---|---|
| `Primary` | bar **and** rail | Dashboard, Members, Classes, Check-in |
| `WideOnly` | rail only; on a phone, reached from its parent screen | Reminders, Intake |
| `DesktopOnly` | rail only, and reachable **nowhere** on a phone — the export drew no mobile screen | Announcements, Equipment |
| `Account` | never a destination row — rail footer group, compact overflow | Staff |

- **At most four `Primary`, and that is a ceiling rather than a preference.** Material caps a bottom
  bar at five and iOS at five-plus-More. There were six here, which on a 448dp screen in Arabic
  ellipsised every label — a row of stubs whose only job was to say which is which. A fifth needs a
  real argument, and `NavigationPermissionTest` fails until someone makes it. The export's fourth is
  **Schedule** and so is ours: once `:feature:classes` was built it took that slot, and Reminders
  moved to `WideOnly` — still one tap from the dashboard's "needs renewal" tile, which is where you
  would look for it anyway.
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
| `permission-denied` | **Live twice, via two deliberately separate string sets.** `States.permissionDenied*` is the route guard: `App.kt`'s `Host` checks `session.can(config.requiredPermission)` before composing any screen and renders the denial instead — the second line of defence behind hiding unreachable nav entries, and what stops a restored back stack landing a coach on the renewal sheet. `Intake.cameraDenied*` is the iOS camera denial via `CameraPermissions`. They are separate because telling someone to ask the gym owner about an OS toggle they control themselves is useless advice. |
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

## Compose layout tests

- **`:core:designsystem` has `jvmTest` Compose UI tests, and they assert *geometry*.** Three layout
  bugs in a row were caught only by looking at a screen — the screen header running its title into
  its action button, intake dropping its source pane below the breakpoint, and the desktop window
  opening on the phone layout. A test cannot tell you a screen looks wrong, but it can tell you two
  things that must not touch are touching. Deps are `libs.bundles.test.composeUi` plus
  `compose.desktop.currentOs`, which is **required** — these really compose and measure, so without
  Skiko's native renderer they fail at class-load rather than on an assertion.
- **JVM target only, deliberately.** Layout is decided by common Compose code and is identical on
  every target, so running these three times buys nothing and would drag a Skiko renderer into the
  iOS test binary. They go green inside `./gradlew check` with no emulator or simulator.
- **Assert a minimum *readable* gap, never `> 0`.** The header bug left a **1dp** gap at the real
  iPhone width — not overlapping, but visually flush — and a first draft asserting `gap > 0` passed
  against the bug. `AnfasScreenHeaderTest` requires 12dp.
- **Test the width the component is actually handed, not the device width.** Every screen wraps its
  header in `padding(horizontal = 16.dp)`, so an iPhone 17 header gets 370dp, not 402dp. The first
  draft used 402dp, where the title fits with 33dp to spare — the collision only exists at 370dp.
  Getting this wrong is the difference between a regression test and a decoration.
- **Prove a new layout test fails without its fix.** Every mistake in this section produced a
  confidently green test that caught nothing. Revert the fix, watch it go red, put the fix back.
- **`Modifier.requiredSize`, not `size`, when the width under test is the point.** `size` is still
  clamped by the incoming constraints of the test surface, so a width wider than the default window
  is silently squeezed — which made `IntakeReviewLayoutTest`'s *desktop* case quietly exercise the
  **narrow** branch and pass for the wrong reason. Caught only because reverting the fix made a
  test fail that had no business failing. A discriminating layout test should fail for exactly the
  branches the bug touched, and pass for the others; if reverting breaks more than you expect, the
  test is measuring something else.
- **Screen-level tests belong in the feature's own `jvmTest`, and reuse the module's existing
  fakes.** `feature/intake-ocr` already had `internal` fakes in `OcrFakes.kt` plus an
  `internal FakeIntakeRepository` that revalidates on read like the real one. Declaring rivals is
  both wasted work and a **compile error**: `jvmTest` sees `commonTest`, and same-package top-level
  names collide across source sets even when both are `private`.
- **A launcher constant can carry an invariant worth testing.** `desktopApp` holds no logic, but
  `WindowSizeTest` asserts its window width against `AnfasBreakpoints.tabletMax` rather than a
  copied `1024` — so moving the breakpoint fails there instead of silently making two
  `DesktopOnly` features unreachable again.

## Dates the user enters

- **A date a human enters comes from `AnfasDateField`, never a text field.** A typed date needs a
  parser, a parser can fail, and every screen then has to carry an "unreadable date" error path —
  the picker deletes all three. `AnnouncementProblem.EVENT_DATE_UNREADABLE` and its string were
  removed rather than left as unreachable code when the announcement composer moved over.
- **The exception is correcting a date read off a photographed sheet.** There the text is a
  transcription of what a human wrote, and the per-cell confidence stripe is the whole point, so
  intake's date cells stay `AnfasInlineEditField`. `IntakeValidator.parseDate` therefore stays too
  — it parses OCR output, not user input.
- **`AnfasDateField` works in UTC start-of-day epoch millis, converted via
  `DatePickerBoundary`.** That is what `DatePickerState` stores natively, so nothing is converted
  twice. The conversion is **always UTC, never the device zone**: a picker selection is a calendar
  date someone pointed at, and running it through `TimeZone.currentSystemDefault()` turns
  "1 September" into 31 August for anyone west of Greenwich.
- **`:core:designsystem` still has no `kotlinx.datetime` dependency.** The field takes `Long?` plus
  a caller-formatted `formattedValue`, because formatting a date is i18n's job and designsystem
  must not depend on `:core:i18n` — the same reason `AnfasSearchField` takes its own placeholder.
- The calendar is stock Material 3, deliberately: the export never drew one, and a hand-restyled
  calendar that is subtly wrong is worse than a correct standard one. It picks up the app's own
  colours from the theme, and its first-day-of-week follows the device locale — on an Egyptian
  device that is Saturday-first, which is correct and differs from a Sunday-first US emulator.

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
- **The desktop database lives in an OS-idiomatic directory, and moves itself there once.** It used
  to be `~/.anfas` on every OS, which is wrong on two of three: macOS has an Application Support
  directory that Migration Assistant and backup tools understand, and a dot-directory in a Windows
  profile can end up synced to OneDrive — which for SQLite means a `.db` being copied out from under
  an open connection, away from its `-wal`. `resolveDesktopDataDir` mirrors
  `AppLog.desktopLogFile`'s injectable-parameter shape so the branching is unit-tested rather than
  discovered on a user's machine. Note **data, not logs**: macOS gets `Application Support` (not
  `Library/Logs`) and Linux `XDG_DATA_HOME` (not `XDG_STATE_HOME`).
- **`adoptLegacyDatabase` is deliberately timid, because every failure mode beats doing nothing.**
  It never overwrites an existing database at the new path (that one is by definition newer), moves
  `-wal`/`-shm` as a set before the `.db` so an interrupted move still leaves the legacy directory
  looking authoritative, and never throws — a permissions problem must not become a failure to
  start. Verified against a real 135KB database: moved with `integrity_check` clean and the schema
  identity hash unchanged.
- **Quitting closes the database, and the close lives on the Koin definition, not in the launcher.**
  `dataModule` declares `single<AnfasDatabase> { … }.withOptions { onClose { it?.close() } }`, so no
  platform entry point has to remember; `desktopApp/main.kt` only has to `lifecycle.destroy()` then
  `stopKoin()` then `exitApplication()`, in that order, so components cancel their scopes before
  the connection goes. Note `onClose` is an extension on `BeanDefinition` reachable only inside
  `withOptions` — the natural-reading `single { } onClose { }` does not compile, which
  `KoinOnCloseContractTest` now pins.
- **The desktop window has a size floor, and it is a reachability rule rather than a cosmetic
  one.** Compose's default window is 800x600, below `AnfasBreakpoints.tabletMax` (1024dp) — so the
  desktop app used to open on the *phone* layout, whose bottom bar carries only the four
  `Placement.Primary` destinations. Announcements and Equipment (`Placement.DesktopOnly`, rail-only
  by design) were therefore unreachable in the desktop app unless you happened to drag the window
  wider. `desktopApp/main.kt` now opens at 1280x840 and sets an AWT `minimumSize` of 1060x680, past
  the breakpoint rather than exactly on it. A feature that vanishes when a window is dragged
  narrower is a bug, not a responsive layout.
- **The desktop window remembers where it was, and the restore is guarded because its bug is
  unrecoverable.** A window restored onto a monitor that is no longer attached cannot be found and
  dragged back — the app appears to launch and do nothing — so `resolveWindowGeometry` decides
  before the window is ever shown. Its rules, each pinned by a test in `WindowGeometryTest`: a
  saved size below the resize floor is raised to it (so restoring can never reintroduce the
  unreachable-`DesktopOnly`-features bug); the window must overlap *some* screen at all, else
  recentre; the size is capped to the screen it lands on but never below the floor; the position is
  then clamped so the title bar stays grabbable. **Negative coordinates are legitimate and
  preserved** — a monitor arranged to the left of the primary has negative x, and rejecting that
  would refuse to restore for everyone with that setup. The overlap gate is deliberately weaker
  than the position clamp: gating on the same threshold made the clamp dead code and recentred a
  window someone had parked off an edge on purpose.
- **Geometry is read from the AWT frame, not from `WindowState`.** `WindowState.position` stays
  `WindowPosition.PlatformDefault` when the platform is what placed the window — exactly the
  first-run case — so the first version saved nothing at all. Found by running the app and reading
  the preferences node, not by reasoning. Reading `window.x/y/width/height` also puts the saved
  values in the same logical user-space units as `GraphicsConfiguration.getBounds()`, so the
  comparison against screen rectangles has no unit mismatch.
- **Persisted continuously (debounced) as well as on close**, because Cmd+Q is how most people quit
  a Mac app and whether that routes through the window's close request is Compose/AWT's business,
  not something to bet the feature on. Storage is the **same `java.util.prefs` node
  (`com/anfas/app`) that `:core:common` backs `Settings` with**, keys namespaced `window.*` — so no
  new dependency and no second storage mechanism. `java.prefs` is in the jlink module list
  (`Contents/runtime/Contents/Home/release`), verified in a packaged build, because prefs failing
  only inside the app image is exactly the kind of bug this would otherwise ship.
- **Reading that plist back with `defaults` or `plistlib` will lie to you** — cfprefsd serves a
  cached copy, so the keys look absent when they are not. Read them from a fresh JVM
  (`Preferences.userRoot().node("com/anfas/app").keys()`) instead.
- **jpackage cannot cross-build**, so `targetFormats` is derived from `OperatingSystem.current()`.
  Declaring Dmg+Msi+Deb together means every host fails on two of three.
- The Room database is excluded from cloud backup and device transfer: the whole domain is PII.

## Android instrumented tests

`androidApp/src/androidTest/.../R8SmokeTest.kt`, run with
`./gradlew :androidApp:connectedStageAndroidTest`. Two tests, and they exist for one reason: R8
output was previously verified only by installing an APK by hand and reading logcat.

- **A test running against a shrunk APK may only touch the app's own entry points.** This is the
  whole lesson, and it cost five failing tests to learn. The first version called `runBlocking`,
  `GlobalContext.getOrNull()`, a `DefaultComponentContext` constructor and `kotlin.test`'s
  assertions; every one failed with `NoSuchMethodError`/`NoClassDefFoundError`, because the app
  never calls them, so **R8 was right to remove them**. Any library API a test reaches for is by
  definition outside the app's reachable graph, so a keep rule bringing it back proves only that
  the keep rule works. What survives: `ActivityScenario.launch` / `recreate`, and
  `org.junit.Assert` (Java, ships inside the test APK, touches nothing shrunk) instead of
  `kotlin.test`, whose asserter lookup needs `kotlin.collections.CollectionsKt`.
- **`recreate()` is the real test of the documented Config-discriminator hazard**, better than any
  assertion about serial names: it drives a genuine save-then-restore of instance state inside the
  minified app, so Decompose's `StateKeeper`, the serializers and their descriptors all have to
  survive shrinking for it to return. It also carries an identity check on the activity instance —
  without it, a `recreate()` that silently did nothing would leave the test green and vacuous.
- **`stage` verifies shrinking, not obfuscation.** AGP disables obfuscation and optimization for
  debuggable build types and says so at configuration time. Verified rather than believed: every
  non-identity entry in `build/outputs/mapping/stage/mapping.txt` is an `R8$$REMOVED$$CLASS$$n`,
  a deletion — not one class is renamed. Shrinking is the failure mode that has actually bitten
  this project (the ML Kit/`ComponentDiscovery` strip), so it is the one covered; a rename
  regression would need a non-debuggable build and a different harness. Don't claim more than this.
- **Two extra rule files, and neither may touch `release`.** `proguard-rules-test.pro`
  (`testProguardFiles`) carries `-dontwarn com.google.errorprone.annotations.**`, which
  `androidx.test` references and nothing here provides. `proguard-rules-stage.pro` (added to the
  `stage` build type only, on top of what `initWith(release)` copied) keeps `kotlin.LazyKt*`:
  kotlin-stdlib is shared between app and test APK so AGP ships it in the app only, the app inlines
  every `lazy` and thus retains no `LazyKt`, and `AndroidJUnitRunner.onCreate` died on it before a
  single test ran. `-dontshrink` in the *test* rules cannot fix that — stdlib is not the test APK's
  program input. Confirm the scoping the same way it was confirmed here: `release`'s mapping still
  shows `kotlin.LazyKt__LazyKt -> R8$$REMOVED$$CLASS$$766` while `stage`'s keeps it.
- **Emulator storage, not correctness, is the usual failure.** Pass
  `-Pandroid.injected.build.abi=arm64-v8a` so one ABI is installed instead of all of them.
- Not in CI yet: `connectedAndroidTest` needs an emulator on the runner. The `jvm` job already
  builds `:androidApp:assembleStage`, so the minified APK is proven to *build* on every PR; proving
  it *runs* is still a local step.

## CI

- **`.github/workflows/ci.yml` is PR-validation only — no release/signing workflows yet.**
  Those need the keystore and Apple Team ID this project still doesn't have (see Release
  configuration above); adding a tag-triggered release workflow before signing material exists
  would just be dead YAML. Three jobs after a wrapper-validation gate, because one runner cannot
  do it all: `jvm` on ubuntu-latest (`check :androidApp:assembleStage`), `apple` on macos-26
  (`iosSimulatorArm64Test linkReleaseFrameworkIosArm64`) — Apple targets silently *skip* on a
  Linux host, which is why this job exists — and `ios-app` (`xcodebuild build`), which catches
  pbxproj/xcconfig breakage no Gradle task sees. All three verified to actually pass by running
  their exact commands locally before committing the workflow, not just by eyeballing YAML.
- **The Apple jobs need `macos-26`, not `macos-15`.** Compose Multiplatform 1.11.1's UIKit layer
  references iOS 26 SDK symbols (`UIViewLayoutRegion`, the `UIUtilities` framework), so linking on
  the macos-15 image's Xcode 16.4 fails with "Undefined symbols for architecture arm64" out of
  `CMPLayoutRegion.o`. This passed locally (Xcode 26.6) and failed only on the runner — the first
  real CI run is what caught it, which is exactly why the workflow's own commands were also run
  locally before committing. Both macOS jobs additionally select the newest installed Xcode and
  **print the version**, so a future image regression reads as one line rather than 800 lines of
  linker output.
- **`xcodebuild` needs a *shared* scheme, and none was committed.** Xcode had only ever written
  `iosApp.xcscheme` under the per-user `xcuserdata/` (correctly gitignored, so invisible to any
  other checkout). Copied verbatim to `app/iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/` — the
  `.gitignore` already carries `!*.xcodeproj/xcshareddata/` specifically to allow this. Do this
  once per Xcode target; a scheme created later must be marked "Shared" in Xcode (or copied the
  same way) or CI silently can't find it.
- **The macOS runner's heap override lives in `$HOME/.gradle/gradle.properties`, appended by a CI
  step — never in the committed `gradle.properties`.** Local dev needs
  `org.gradle.jvmargs=-Xmx8192M` for release framework linking across two iOS targets; a
  constrained CI runner needs less. Lowering the committed value to fit CI would starve local
  builds instead. `org.gradle.parallel` stays unset in both places — parallel execution plus
  Kotlin/Native linking inside one Gradle daemon is how a constrained runner OOMs.
- **Dependency verification is still deferred** (thousands of entries for a KMP project this
  size, regenerated on every bump) — `dependabot.yml` covers gradle + github-actions ecosystems
  instead, weekly. It earns its place on one dependency specifically: `composeMaterial3` is an
  **alpha** shipping in production UI on all three platforms, and a PR the day a stable version
  lands is worth having.

## Commands

```
./gradlew build                                    everything
./gradlew check                                    tests + layering rules
./gradlew :androidApp:assembleDebug                Android
./gradlew :androidApp:connectedStageAndroidTest -Pandroid.injected.build.abi=arm64-v8a
                                                   R8 smoke tests, needs an emulator
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
