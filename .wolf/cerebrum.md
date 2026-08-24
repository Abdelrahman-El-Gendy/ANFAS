# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-08-21

## User Preferences

<!-- How the user likes things done. Code style, tools, patterns, communication. -->

## Key Learnings

- **Project:** ANFAS
- **Description:** This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM) and a Ktor server.
- **Design source of truth:** `design/stitch/` holds the Stitch export. `design.md` is the
  theme spec (front-matter tokens + prose); `export/<screen>/index.html` are the 23 screen
  exports; `TOKENS.md` is **generated** from them — re-derive it, never hand-edit.
  `:core:designsystem` is generated *from* these. Change Stitch, re-export, then the Kotlin.
- **Stitch project:** `5240614381299664566` "FAHD Professional Management Suite", theme
  "Aurelian Performance". 25 screens: 23 with markup, 1 reference photograph, dark mode only.
- **Every Stitch screen embeds its own `tailwind.config`.** That is where the exact tokens
  live, and screens can disagree with each other and with `design.md`. Always cross-check all
  screens and take the majority rather than trusting one screen or the prose.
- **design.md's prose palette contradicts its own token block.** The prose describes
  `#12110F`/`#1C1A17`/`#F5F1EA`/`#252320`; the tokens and all 23 screens use the M3 set
  (`#141311`/`#211f1d`/`#e6e2de`/`#2b2a27`). The M3 set is the base; the prose colours survive
  only as brand accents used as raw hex (`#f5f1ea` alone appears 22 times in the markup).
- **`data-mono` is not a mono font.** It resolves to IBM Plex Sans in every screen; its intent
  is digit alignment, implemented as `fontFeatureSettings = "tnum"`.
- **Fonts:** Google Fonts ships IBM Plex Sans only as a variable font now. Static weights come
  from `IBM/plex` at `packages/plex-sans/fonts/complete/ttf/`. Compose resource filenames must
  be lowercase_with_underscores, and only font files may sit in `composeResources/font/`.
- **Four screens have no module in the graph:** staff-login, staff-dashboard,
  reception-dashboard, live-checkin-log. Four more (sync-conflict, offline-banner,
  session-expired, permission-denied) are cross-cutting states belonging to `:core:designsystem`.
- **`gradle/version.properties` is the single source of truth for the app version.** Android
  reads it via `com.anfas.buildlogic.appVersion()`, desktop uses it for `packageVersion`, and
  iOS gets a **generated, committed** `app/iosApp/Configuration/Version.xcconfig` because Xcode
  reads xcconfig before any build phase runs. `verifyIosVersionConfig` is wired into `check`, so
  drift is a build failure. Regenerate with `./gradlew generateIosVersionConfig`.
- **versionName must be semver MAJOR.MINOR.PATCH with MAJOR > 0** — jpackage rejects anything
  else, which is why it is "1.0.0" not Android's old "1.0". Validated at configuration time.
- **Formatting gate is Spotless + ktlint**, inherited by every module through `anfas.quality`
  (applied from `anfas.kmp.library` and `anfas.jvm.server`). Rules live in `.editorconfig` AND
  are duplicated as `editorConfigOverride` in the plugin, because Spotless does not reliably
  resolve `.editorconfig` sections for the virtual paths it hands ktlint.
- **Three ktlint rules are disabled on purpose:** `function-naming` (@Composable is PascalCase),
  `filename` (files group related declarations here), `kdoc` (every module build file opens with
  a `/** */` header).
- **`const val` must be SCREAMING_SNAKE_CASE** — `kotlin.code.style=official` implies it and
  ktlint enforces it. Compose layout weights are not an exception.
- **Logging goes through `AppLogger` in `:core:common`**, never Kermit directly. `configureLogging`
  exposes no Kermit type so Kermit stays `implementation`. **Never log member names or phone
  numbers** — the whole domain is PII and the desktop sink writes plaintext to disk.
- **Desktop needs a file log sink**; Kermit's JVM writer targets stdout and a packaged app has no
  terminal. Path is OS-idiomatic (`~/Library/Logs/ANFAS`, `%LOCALAPPDATA%`, `XDG_STATE_HOME`) and
  resolved by a pure, tested function.
- **`installCrashHandler()` is called by all three launchers** before DI. Android delegates to the
  previous handler (replacing it suppresses the system crash dialog); desktop shows the log path;
  iOS re-terminates so the OS still records a crash report.
- **Every component scope carries `appExceptionHandler(tag)`.** It is a net, not a fix — the
  screen still freezes, so flow chains that can fail also need `catch` and an error state.
- **`:core:auth` and `:core:network` are intentionally inert and NOT depended on by `:composeApp`.**
  Each says so in its own source. Re-add the edge in the commit that first uses it.
- **Bash cwd persists between calls in this harness.** A relative `cd` in a later call is
  resolved against the previous call's directory, not the repo root. Use absolute paths.
- **`:core:data` is the data seam.** `anfas.kmp.feature` grants `:core:model`, `:core:common`,
  `:core:designsystem` and `:core:data` — and deliberately withholds `:core:database` and
  `:core:network`. A feature that needs data gets a repository interface from `:core:data`;
  never add the lower two to the convention plugin.
- **The three platform launchers all call `RootComponent(context)`** (MainActivity, desktop
  `main.kt` on the EDT, iOS `MainViewController`). To avoid touching all three plus the Swift
  side, `RootComponent` is a `KoinComponent` and `by inject()`s feature component factories.
  That service-locator lookup is confined to the app shell; features take constructor deps.
- **Features expose a `*ComponentFactory`, not the component.** The router then needs no
  knowledge of a feature's dependencies, and components stay Koin-free and unit-testable.
- **Decompose root is a `childStack`, not a `childSlot`** — back has to pop.
- **Components are Koin `factory`, never `single`.** Each owns a coroutine scope bound to its
  Decompose lifecycle; a singleton would leak it across screens.
- **The design's brand name is inconsistent across screens** — "STRIVE STAFF"
  (member-directory), "Titan Gym / Staff Portal" (members-list-empty, search-no-results),
  "FAHD Gym" (page titles). Treat it as placeholder churn; don't hardcode one.
- **Desktop/mobile shells differ in the export:** a 64-wide fixed sidebar on desktop, a bottom
  nav (Dashboard/Members/Schedule/Check-in) on mobile. Both are app-shell concerns for
  :composeApp, not feature concerns.
- **Shared presentation helpers live in `:core:common`.** `RelativeTime` (Today/Yesterday/N
  days ago labels) and `MoneyFormat` are used by more than one feature, and features must
  never depend on each other. `RelativeTime.format` takes a `separator` because the export
  uses ", " in the members table and " " in the reminder queue.
- **Money is minor units (piastres) in a `Money` value type**, never a Double. `MoneyFormat`
  drops decimals for whole amounts ("600 EGP") and uses U+2212 MINUS for discounts ("−140 EGP").
- **Two different "discount" concepts on the renewal sheet.** `SubscriptionPlan.savingsPercent`
  is the "Save 8%" badge comparing a multi-month plan against paying monthly — it never changes
  the price. `RenewalQuote.discount` is a staff-applied amount that does reduce the total.
  The export's numbers: 1,650 − 140 = 1,510.
- **Retry is not always allowed.** `FailureReason.isRetryable` is true only for RATE_LIMITED;
  opt-in, invalid number and paused-template failures need a human first. The filtering lives
  in `ReminderRepository.retry`, which re-reads current state and returns how many were
  actually requeued — a bulk selection legitimately mixes retryable and not.
- **Subscriptions are append-only history.** The "current" term is simply the row with the
  latest `ends_on_epoch_day`; that is what makes "start when current ends" computable and
  preserves what was charged. `confirmRenewal` takes the displayed quote so a price change
  between quoting and confirming cannot alter the charge.
- **`SubscriptionPlanSeed` seeds the plan catalogue at startup** via a `createdAtStart`
  Koin single on the IO dispatcher. Plans live in the DB so prices change without an app
  build, but the table has to start somewhere and there is no plan-management screen.
- **Nav shell:** `AnfasNavRail` at >= `AnfasBreakpoints.tabletMax` (1024dp), `AnfasBottomNav`
  below. Only destinations that exist are listed. Top-level switches use
  `navigation.replaceAll`; detail routes use `push` so back pops.
- **Modals are component state, not routes.** The failed-reminder dialog is
  `ReminderQueueState.openedFailure` — it is an overlay on the list it belongs to, and routing
  would put a screen transition in the back stack for something drawn as a dialog.
- **OCR intake validation is relational, so it revalidates the whole batch.** A phone number
  is only a duplicate with respect to other rows *and* the existing membership, so editing one
  row can clear or create an issue on a different row. `IntakeValidator.validate(rows,
  existingPhones)` takes the whole list; there is deliberately no per-row entry point.
- **Issues are blocking or advisory, and that split is what makes "6 of 8 ready" work.**
  Blocking: missing name/phone, duplicate phone (in the batch or against the membership), end
  date before start. Advisory: unreadable date, unknown plan, low confidence.
- **`IntakeRepository` validates on *read*, never trusting stored issues.** The membership
  changes independently of a scanned sheet, so a cached "ready to import" flag would eventually
  let a duplicate member through. The cached `issues` column exists only for list-screen counts.
- **`Member.phone` + `members.phone_normalised`.** The normalised column is written by
  `Member.toEntity()` using `IntakeValidator.normalisePhone`, reusing the same rule intake
  compares against — two different normalisations would silently stop matching. It handles
  "+20 100…" / "0100…" / "01001234567" as one number.
- **`IntakeReviewComponent` shows the oldest sheet still under review** rather than taking a
  batch id. Sheets are a work queue, not addressable documents, and oldest-first surfaces a
  forgotten sheet instead of burying it.
- **"Trial" is a valid intake plan label but not a `PlanTier`.** It appears on the sheets but is
  not purchasable through the renewal sheet, so `IntakeValidator.KNOWN_PLAN_LABELS` accepts it
  without forcing it into the tier enum.
- **`@Embedded(prefix = "…")` works in Room KMP** — `IntakeFieldColumns` gives `name_value`,
  `name_confidence`, `name_was_edited` etc., keeping five OCR cells in one flat table without
  fifteen hand-named columns. `ForeignKey(onDelete = CASCADE)` also works.





## Do-Not-Repeat

<!-- Mistakes made and corrected. Each entry prevents the same mistake recurring. -->
<!-- Format: [YYYY-MM-DD] Description of what went wrong and what to do instead. -->
- [2026-08-21] Do not trust `mcp__stitch__download_assets`. It reports "Assets downloaded to <dir>"
  and writes nothing. Fetch `htmlCode.downloadUrl` from `list_screens` with curl instead.
- [2026-08-21] Do not add Stitch as an `http` MCP server pointing at `https://stitch.googleapis.com/mcp`.
  That endpoint does not support dynamic client registration, so Claude Code can never
  authenticate. Use the `@_davideast/stitch-mcp proxy` stdio server with `STITCH_API_KEY`.
- [2026-08-21] An injected id generator in a test must be unique per call. A constant `newId` made a
  Room upsert collapse every imported member into one row, and the failure read like a product
  bug rather than a fixture bug.
- [2026-08-21] When patching source with `str.replace()`, assert the anchor occurs exactly once (or
  pass a count). A duplicated import block produced "Conflicting import ... is ambiguous".
- [2026-08-21] A StateFlow under `combine()` keeps emitting after the emission you asserted. End
  every Turbine `state.test { }` block with `cancelAndIgnoreRemainingEvents()` or the test
  fails with "Unconsumed events found".
- [2026-08-21] Do not rely on a **transitive** `api` dependency inside commonMain. `:core:common`
  reached kotlinx-datetime through `api(project(":core:model"))`; `jvmTest` compiled but
  `compileCommonMainKotlinMetadata` failed. Declare ABI dependencies explicitly per module.
- [2026-08-21] `todayIn` is an extension on `Clock`, not on `Instant` — `clock.todayIn(zone)`.
- [2026-08-21] When extracting a shared helper, delete the original. Moving `runStorage` into
  StorageBoundary.kt while a `private` copy stayed in OfflineFirstMemberRepository.kt produced
  a wall of "overload resolution ambiguity" errors in the untouched file.
- [2026-08-21] Backtick-quoted test names must not contain `,` `;` `:` `.` — Kotlin/Native rejects
  them ("Name contains illegal characters"). `jvmTest` passes anyway, so the break only shows
  up when `check` compiles the native test targets. Run `./gradlew check`, not `:x:jvmTest`,
  before calling a KMP suite green.
- [2026-08-21] `kotlinx.datetime.Month` has no `.number` in 0.8.0 — index by `ordinal`.
- [2026-08-21] A KMP module exposing a type that *extends* a dependency's class must declare that
  dependency `api`, not `implementation`. `:core:database` had room3 as `implementation` while
  `AnfasDatabase : RoomDatabase`, so `:core:data` could not compile.
- [2026-08-21] `./gradlew :x:jvmTest` reporting BUILD SUCCESSFUL does not mean tests ran — it is
  often UP-TO-DATE or NO-SOURCE. Confirm by reading `build/test-results/**/TEST-*.xml`, or
  re-run with `--rerun-tasks`.
- [2026-08-21] When parsing Stitch's embedded `tailwind.config`, keys are *sometimes* quoted and
  sometimes bare (`colors:` vs `"colors":`). A regex requiring quotes silently drops whole
  screens — it reported 21/23 coverage when the real answer was 23/23.


## Decision Log

<!-- Significant technical decisions with rationale. Why X was chosen over Y. -->
- [2026-08-21] **Amended the uncommitted v4 schema to add `members.phone` instead of adding a v5.**
  Only `1.json` is tracked in git; v2-v4 were generated this session, so folding the column
  into v4 avoided a migration that exists only to correct an unshipped one.
- [2026-08-21] **Import creates Members only — not subscription terms.** The sheets carry start/end
  dates and a plan, which are validated and shown, but turning them into a `SubscriptionTerm`
  needs a price for "Trial" that the design never states. Inventing one would put wrong money
  in the ledger. **This is the open question blocking full intake value.**
- [2026-08-21] **No "Merge" action on a duplicate row**, though the export offers one. Merging two
  member records is destructive with no defined semantics anywhere in the design — which
  fields win, what happens to the other's history. Correcting the phone is offered instead.
- [2026-08-21] **No pan-tool button.** The source view is directly draggable and pinch-zoomable, which
  is fewer controls and works on touch, unlike a mode toggle.
- [2026-08-21] **Membership numbers are "one past the highest existing".** A placeholder: the export
  shows numbers around 88xxx with no stated scheme, and prefixes, check digits or per-branch
  ranges are a business decision.
- [2026-08-21] **The split view stacks below 1024dp** — the export is desktop-only at 2560px, and a
  40/60 split of a phone screen makes both halves useless. Below the breakpoint the table wins
  and the source pane is dropped.
- [2026-08-21] **Queue tabs follow `whatsapp-reminder-queue` (Queued/Sent/Failed), not
  `reminder-queue-empty` (Pending/Sent/Failed/Archived).** The two exported screens disagree;
  the former has the real table, and "Archived" has no data or behaviour anywhere.
- [2026-08-21] **Plan savings are computed for every tier, not just Quarterly.** The export badges
  only Quarterly ("Save 8%") but Annual genuinely saves 19% against monthly. Hiding that would
  cost the member money. Store null to suppress a badge if the design really means one only.
- [2026-08-21] **Icons remain hand-built.** 13 more added for this feature; all were simple geometry.
  A real icon source is still needed for complex glyphs (document_scanner, fitness_center,
  card_membership) before the remaining features.
- [2026-08-21] **Omitted controls with no backing behaviour**, consistent with the members footer
  decision: no "Send test message" / "Run Queue" buttons and no "Daily job last ran 06:00"
  line, because no send job exists. Also no discount input — the export shows a "−140 EGP"
  line with no control anywhere to set it, so the arithmetic is implemented and tested while
  the UI waits for a source.
- [2026-08-21] **Template filter is a chip row, not a `<select>`.** There is no select component in
  the design system and five chips read fine at both form factors.
- [2026-08-21] **Failed-reminder retry block is explained inline, not in a tooltip.** The export uses
  a hover tooltip, which is unreachable on touch.
- [2026-08-21] **Members row tap opens the renewal sheet, temporarily.** The member profile screen
  exists in the export but not in the app. When it lands, that becomes the row's route and
  renewal moves behind the row's overflow menu.
- [2026-08-21] **Token conflicts resolved by screen majority, not by prose.** `background` =
  `#141311` (22/23; staff-login's `#12110F` rejected). `error` = `#ffb4ab` (22/23;
  failed-reminder-detail's raw `#ef4444` rejected). Radius scale taken from the exports
  (4/8/12/pill), not design.md's front-matter, which is shifted one step.
- [2026-08-21] **M3 *fixed* colour roles live in `AnfasExtendedColors`, not `darkColorScheme()`.**
  They are real Stitch tokens but not stable `darkColorScheme` parameters; routing them
  through a data class avoids depending on an experimental M3 API.
- [2026-08-21] **`large`/`extraLarge` shapes deliberately resolve to 12dp.** The design defines no
  radius above 12dp; mapping them to invented larger values would let components drift off-spec.
- [2026-08-21] **M3 typography slots the export does not specify keep M3 metrics and only swap the
  family.** Inventing sizes for undefined slots would put values in the repo no screen backs up.
- [2026-08-21] **Introduced `:core:data` rather than putting repository interfaces in `:core:model`
  or `:core:common`.** `:core:model` may depend on nothing, so it cannot reference `AppResult`;
  `:core:common` would accumulate every feature's contracts. CLAUDE.md's "add the seam to
  `:core:*`" is satisfied by a dedicated module.
- [2026-08-21] **Icons are hand-built ImageVectors in `AnfasIcons`.** JetBrains stopped publishing
  `org.jetbrains.compose.material:material-icons-*` at **1.7.3** while the project is on
  Compose 1.11.1; pulling a 1.7.x Compose artifact into an 1.11 runtime is not worth it, and
  material-icons-extended is huge and deprecated. Only simple geometric glyphs were built —
  complex ones (document_scanner, card_membership, fitness_center) still need a real source.
- [2026-08-21] **Nothing-states are separate sealed cases, not an empty list plus flags.**
  `DirectoryEmpty` and `NoMatches` are drawn completely differently, and are told apart by
  whether the query was blank — which is also why `MemberRepository` has no `isEmpty()`.
- [2026-08-21] **`MembersListState.query` sits outside `MembersListContent`** so the text field keeps
  the user's characters while a debounced search is still resolving.
- [2026-08-21] **Two documented departures from the export in the members table:** weighted columns
  instead of `min-w-[600px]` + `overflow-x-auto` (nesting horizontal scroll around a lazy
  vertical list is fragile), and a count-only footer instead of "Showing 1 to 4 of 128" with
  pager arrows (paging is not implemented; dead arrows are worse than none).
- [2026-08-21] **Unknown `status` strings map to PAUSED instead of throwing.** One bad row must not
  take down the whole directory.
- [2026-08-21] **AnfasDatabase v2 uses `AutoMigration(1, 2)`**, not a destructive fallback. The
  committed schemas are the contract; dropping user data is never the default.
- [2026-08-21] **Only 400/500/600 IBM Plex Sans weights are bundled** — the three the ramp uses.
  Adding a weight means adding a TTF, not letting Compose synthesise one.


## Key Learnings — 2026-08-22 (OCR intake, verified on device)

- **Run the app.** Three layout bugs and two data-integrity bugs in this session were invisible to
  426 unit tests and only appeared on a real emulator. `adb exec-out screencap -p > file.png` plus
  reading the PNG is the fastest loop; `adb` is not on PATH — use `~/Library/Android/sdk/platform-tools/adb`.
- **`Modifier.fillMaxWidth()` inside a `Row` child resolves against the *incoming* max constraint,
  not the sibling's width.** The first child then claims the whole row and later children are
  measured at ~one character. Bound the child with `width(IntrinsicSize.Max)`. This bit twice in
  one session (AnfasTabs indicator, and the intake footer via unweighted `Text` under `SpaceBetween`).
- **Unweighted children in a `Row` are measured first, at full intrinsic width.** A long label
  beside buttons starves the buttons. Give the label `weight(1f)`.
- **`FlowRow` is the default for action pairs**, not `Row` — two buttons side by side compete for a
  phone's width and the loser wraps its label to a different height.
- **ML Kit returns `Text.Line`s that fuse whole columns together** when horizontal gaps are small.
  Always emit word-level `Text.Element` boxes. Measured on a printed A4 sheet: word gaps 0.005–0.009
  of page width, column gaps 0.017+.
- **An OCR parser must group boxes into cells by gap before classifying anything**, or it is coupled
  to one engine's idea of a "line" — Vision returns lines, ML Kit returns words.
- **A name cell must reject any digits.** A blank name raises MISSING_NAME and reaches a human; a
  member silently imported as "Omar Hassan 2023Monthly" does not.
- **Six-column tables need a minimum width and horizontal scroll on phones** (`AnfasTableScroll`).
  Wrap header + rows only — a scrolled footer hides its own action buttons.
- **Catalog versions with no consumer hide incompatibilities.** `androidx-core 1.19.0` sat unused
  until `:core:ocr` needed FileProvider, then failed `checkAarMetadata` against our AGP 9.0.1 ceiling.
- Synthetic test images can over-fit the algorithm: the first generated sheet had columns 11px apart,
  which ML Kit fused. Give fixtures realistic ruled-column spacing.

## Key Learnings — 2026-08-22 (Phase 4, release config)

- **R8 failures are warnings, not crashes.** ML Kit's ComponentDiscovery logged
  `NoSuchMethodException: ...TextRegistrar.<init>` at W level and OCR silently produced nothing.
  Never conclude R8 is fine because the app launches — install the minified APK and exercise the
  real feature path while watching logcat.
- A `stage` build type (minified + debuggable + debug-signed + `testBuildType`) is the only way to
  test R8 output; `connectedAndroidTest` needs a debuggable APK and `release` must not be one.
- **Inside a `.kts`, `java` resolves to JavaPluginExtension** and shadows the package — import
  `java.util.Properties`, never fully-qualify it. ktlint wants java imports last, and a comment
  inside the import block makes it refuse to autocorrect at all.
- Compose Multiplatform on iOS: insets belong to **Compose**, not SwiftUI. `.ignoresSafeArea()`
  on the hosting view plus `safeContentPadding()` in Compose. Restricting it to `.keyboard` leaves
  the window background showing as white bands.
- `Modifier.weight` divides a **bounded** parent. A wrap-content root silently disables both the
  weight and any `verticalScroll` inside it — content overflows and never scrolls. This only shows
  up in landscape, where the content no longer happens to fit.
- Emulator rotation: `settings put system accelerometer_rotation 0` first, and re-check it — it
  reverts to 1 and silently overrides `user_rotation`. Confirm via the screenshot's dimensions.
- iOS bundle ids are registered once and never change. Settle them before the first upload; the
  KMP wizard's `com.anfas.app.ANFAS$(TEAM_ID)` pattern resolves to something malformed.

## Key Learnings — 2026-08-23 (dashboards, RBAC)

- **A dashboard is where a fake number does the most damage** — it will be believed and acted on.
  Only ship tiles derived from stored data, and say in the KDoc what was left out. `staff-dashboard`
  was declined entirely for this reason.
- **One failing seam should fail the whole dashboard.** Omitting a tile makes the missing number
  read as zero, and zero on a "needs chasing" tile means the opposite of the truth.
- **Domain judgements like "expiring soon" belong in `:core:model`**, shared by every screen that
  renders them. Two definitions is how a dashboard disagrees with the member it links to.
- **Two permissions on one screen defeat a route guard.** Scan/Import and View/Retry both live on a
  single route, so they must be gated in the component — and enforced there, not just hidden,
  because a component method is callable from anywhere.
- **Room: never alias a subquery `inner`.** The query verifier reads it as `INNER JOIN`.
- `adb shell input text` is unreliable against Compose `BasicTextField`, especially in dialogs —
  the IME commits autocorrect suggestions. Verify multi-field flows by test; use the device for
  layout, navigation and RTL.
- My own repeated mistake: heredoc Python with `\\"` inside a JSON string literal fails to parse.
  Use plain quotes in buglog text.

## Key Learnings — 2026-08-23 (navigation IA)

- **A bottom bar divides width equally, so its item count is a hard constraint, not taste.** Six
  items on a 448dp phone gives each ~74dp, which is less than an Arabic label needs — the bar
  degrades into six ellipsised stubs whose only job was to say which is which. Material caps at
  five, iOS at five-plus-More.
- **Cut the bar by asking which entries are *places*, not which are least important.** The three
  kinds of thing that get conflated into tabs: destinations, screen actions ("scan a sheet" belongs
  to the directory), and account-level things (language, staff, sign out). Separating them removed
  two tabs without removing anything from the app.
- **Moving an entry point is not deleting a route.** Keep the `Config`, its `@SerialName` and its
  `requiredPermission`; change only how it is entered. A test asserting the permissions still hold
  is what stops "reduce the bar" turning into "lose two features".
- **When an action moves onto a screen, check the permission pair.** SCAN_INTAKE is now only
  exercisable from the directory, so every role holding it must also hold VIEW_MEMBERS — otherwise
  it is a permission with no way to use it. Asserted in `NavigationPermissionTest`.
- **A pushed screen must not repeat its title.** Top bar title + `AnfasScreenHeader` title on the
  same screen reads as two screens stacked. Top bar carries the title; the body keeps the subtitle.
- **A rail and a bar may legitimately differ in affordance, not in architecture.** 256dp has room
  to spell out the account actions; a phone does not, so it gets an overflow. Same four
  destinations, same account group, different chrome.
- **A compile-time-complete string interface cannot catch a key that is never called.**
  `showingMembers` and its six Arabic plural forms existed while the call site hardcoded English.
  Only running the app in the other language finds these — read a `uiautomator` dump of the Arabic
  build and look for the one Latin string among the Arabic ones.

## Key Learnings — 2026-08-23 (desktop is the primary form factor)

- **The user considers desktop the core of this product.** When the rail and the bar disagree,
  follow the rail. Extract and check the desktop export first.
- **Stitch generates each screen independently, so the export is not self-consistent.** Across 16
  desktop screens the sidebar had four different item lists and the brand was called four different
  things ("Iron & Amber", "Titan Gym", "STRIVE", "FAHD Admin"). Derive the *canonical* structure from
  the most frequent and most complete instance; do not treat any single screen as authoritative.
- **The canonical desktop sidebar** is Dashboard, Members, Subscriptions, Intake, Classes, Recovery,
  Equipment, Announcements, then an `mt-auto` footer group (Settings/Support/Logout) with an
  avatar + name + role block.
- **The export's own bottom bar is four items and its sidebar is eight.** Different lists per form
  factor is the design's position, not a compromise — modelled here as `TopLevel.placement`.
- **Prefer a join over a new persisted field for display data.** A name on `Session` would sit in
  unencrypted `Settings` and go stale on rename; `observeCurrentStaff()` joins the session id
  against `staff` and is always fresh.
- **Avatars: the export fills every one with a generated photograph and nothing in this app uploads
  one.** Initials are what the data supports. `AnfasAvatar(initials)` keeps designsystem domain-free.
- **Two divider tokens, not one.** `white/5` between table rows, `white/10` on chrome edges.
- Verify a wide layout without touching the user's screen: `adb shell wm size 2560x1600` +
  `wm density 240` gives ~1706dp and a real `uiautomator` tree. Always `wm size reset` /
  `wm density reset` afterwards. `screencapture` grabs the frontmost window, which is whatever the
  user is actually doing.

## Key Learnings — 2026-08-24 (classes feature)

- **A grid/list breakpoint must be derived from what it actually needs to draw, not borrowed from
  the rail/bar breakpoint.** `AnfasBreakpoints.tabletMax` (1024dp) decides rail-vs-bar and is
  measured against the *window*; a screen hosted inside that layout only ever gets
  window-minus-256dp. Reusing it for the week grid's own compact/wide switch made the grid
  unreachable on any window under ~1280dp, including a 13" iPad in portrait/landscape. Compute a
  content-derived minimum instead (hour axis + N columns of a legible minimum width) and compare
  the *local* `BoxWithConstraints` width against that.
- **Kotlin's `${'$'}count` escape produces the literal text, not an interpolation** — it compiles
  clean and every existing test still passes, because nothing asserted the *content* of a
  translated string, only its type. Caught only by running the built UI in Arabic. Added
  `PlaceholderInterpolationTest` in `:core:i18n` asserting no rendered string contains a literal
  `$` and that quantified/parameterised strings contain their argument — this is now a permanent
  regression guard, not a one-off fix.
- **A recurring weekly timetable is not a series of dated occurrences.** `GymClass` stores
  `dayOfWeek` + `startsAt`, matching both designed screens exactly (`class-schedule` asks "what's
  today", `weekly-class-schedule` repeats across days with no notion of *which* week). The
  documented cost: no way to cancel a single date or handle a holiday — that needs an exceptions
  table, added when someone asks, not guessed at now.
- **The overlap/lane-layout algorithm for a calendar grid is the part worth unit-testing
  hardest**, because a wrong lane assignment doesn't look wrong — it silently draws one class on
  top of another and the covered class disappears. Test the *invariant* (overlapping items never
  share a lane; everything in one overlap group agrees on lane count) rather than a specific lane
  number, or the test itself becomes as fragile as the bug it's meant to catch.
- **A domain field that should follow a live rename (`instructorStaffId`) gets no foreign key
  for a different reason than one that must survive a delete (`CheckIn.memberName`).** Check-ins
  copy data because history must never change; classes reference `staff.id` with no FK because a
  disabled coach must not cascade-delete next week's schedule — the repository resolves the name
  at read time and an unresolvable id reads as "unassigned", which is a real, actionable state.
- **Verifying on-device without touching the user's screen**: `screencapture`/AppleScript require
  screen-recording/assistive-access permission this environment doesn't have (returns black
  frames or a permission error) — same lesson as the earlier Android `screencap` bug, now
  confirmed on macOS/iOS too. Working alternative found this session: XcodeBuildMCP's
  `snapshot_ui` gives a real accessibility-tree snapshot with tappable element refs, but has no
  tap action of its own in this environment; `mcp__mobile-mcp__mobile_click_on_screen_at_coordinates`
  (coordinates read off the snapshot) supplies the tap. `xcrun simctl io <udid> screenshot` DOES
  work for a static picture when a tree isn't enough. Never trust a black/empty screencapture as
  proof of a blank UI — check the accessibility tree or an `io screenshot` first.
- **Never leave a verification-only hack in navigation logic.** Forcing `landingFor()` to always
  return a specific destination (to reach a new screen quickly on a fresh device) is fine as a
  scratch edit but must be reverted before the branch is considered done — grep the diff for it
  before committing, since it silently breaks sign-in for every other role.

## Key Learnings — 2026-08-24 (therapy feature)

- **A designed rail entry point does not obligate building every screen that entry point implies.**
  The canonical desktop rail (documented earlier) lists "Recovery," and it would have been easy to
  read that as "build a caseload roster." The export only ever drew the single-patient case file —
  no roster — so the roster was not built, and the case file is reached from the member's own
  profile instead, same pattern as Renewal. Check what was actually *drawn*, not just what a nav
  label implies exists.
- **Two aggregate roots can choose opposite foreign-key shapes for principled reasons, not
  inconsistency.** `check_ins` has no FK to `members` because a visit's history must survive a
  deleted member. `therapy_cases` CASCADEs from `members` because clinical narrative with no
  member to attach to isn't a record worth keeping. Both are "no dangling data," just applied to
  different questions: check-ins ask "did this happen," therapy asks "is this still relevant."
- **One coarse permission beats two fine ones when the design itself doesn't distinguish them.**
  The export's own banner says "therapist and owner access only" as a single bucket — no
  view-only tier — so `VIEW_THERAPY` gates viewing AND editing/logging/closing. Classes needed
  `MANAGE_CLASSES` separate from viewing because *everyone* on shift needs to see the timetable
  but only some should move a class; therapy has no such split in the design, so inventing one
  would add a permission tier nobody asked for.
- **Fix an RTL wording ambiguity before it ships, by reasoning about it, not by waiting to catch
  it on device.** A `"$first → $latest"` pain-score trend reads backwards in Arabic RTL with a
  directional arrow. Rewriting as `"من $first إلى $latest"` (real words: "from X to Y") sidesteps
  the whole direction question, the same fix pattern as autoMirror icons but applied to text.
  Caught this by reasoning about RTL semantics *before* writing the device-test seed data, not
  after seeing it render wrong — worth doing proactively for any "before → after" style string.
- **A `Canvas` sparkline plotting real recorded values is honest visualization, not the fabricated
  numbers this codebase has repeatedly refused to ship** (staff-dashboard's 4 invented figures,
  the "14/20" capacity bar with no booking system). The line is: does every point come from a
  number a human actually entered? If yes, draw it; if the data to back a designed visual doesn't
  exist, omit the visual and say why in the KDoc.
- **`snapshot_ui` (XcodeBuildMCP) has no tap of its own in this environment; pair it with
  `mcp__mobile-mcp__mobile_click_on_screen_at_coordinates`** using the coordinates the snapshot
  already reports. Re-run `mobile_list_elements_on_screen` after each tap rather than reusing
  stale coordinates — a dialog opening changes every coordinate on screen.
- **Avoid typing into Compose text fields via the simulator/emulator IME during verification** —
  already known unreliable (autocorrect commits wrong values). Verify data-entry *logic* with unit
  tests (already thorough here) and use the device only to confirm layout, navigation, RTL, and
  state transitions that don't require typing — chip selections, button taps, dialog open/close,
  and reading back rows seeded directly via SQL into the simulator's own sqlite file.

## Key Learnings — 2026-08-24 (announcements feature)

- **A new `Placement` category is justified the same way a new permission is: check what the
  export actually drew, not what feels analogous.** `create-announcement` has `deviceType: DESKTOP`
  only, with no mobile screen anywhere in the export — unlike every other feature so far, which had
  at least a phone-reachable path. That is what earned `Placement.DesktopOnly` (rail only, zero
  mobile path), rather than reusing `WideOnly` (rail + reachable from a mobile parent) which would
  have implied a mobile entry point that doesn't exist.
- **"Publish" repeats the WhatsApp Reminder Queue precedent: build the real staff-facing
  composition/audience layer even when delivery has nobody to reach.** `publish()` computes and
  freezes a real `recipientCountAtPublish` against live members/terms — never a placeholder number
  — but there is no push/WhatsApp channel wired to actually notify anyone. The number is honest
  even though nothing downstream consumes it yet.
- **A "no unpublish" rule must be enforced in the component, not just hidden in the UI** — see
  `bug-announcements-publish-guard` in buglog. A dialog withholding a button is a good UX signal but
  not a guarantee; `onRequestPublish()`/`onRequestDelete` themselves must refuse the illegal
  transition, because a component method is callable from anywhere (a future screen, a test, a
  future contributor who reuses the component). Same principle as `CheckInPolicy` deciding the
  outcome instead of the caller.
- **Turbine + a component whose `StateFlow` has two independent `combine` sources feeding off the
  same upstream `ui` state (here: the form itself, and `ui.flatMapLatest { repository.observeReach
  (audience) }`) emits twice per mutation** — once with the old value from the second source, once
  after that source's flow delivers its real value. A test that calls a mutator once and then
  `awaitItem()`s once will see the *stale* intermediate emission, not the settled one. Drain to the
  settled state (loop `awaitItem()` while the field you care about is still catching up) rather than
  assuming one mutation = one emission.
- **`CreateAccountOutcome`/`StaffChangeOutcome` live in `com.anfas.core.data`, not
  `com.anfas.core.auth`**, even though `Role`/`Session`/`StaffAccount`/`SignInResult` are in
  `core.auth`. `AuthRepository`'s own return-type sealed interfaces are declared alongside it in
  `core.data`, not in the domain module — check the actual declaring file before importing rather
  than assuming a family of related types share one package.

## Key Learnings — 2026-08-24 (equipment feature)

- **Check `design/stitch/TOKENS.md`'s screen-inventory table before assuming a screen's
  `Placement`.** `equipment-detail` is listed `D` (desktop), exactly like `create-announcement` —
  that one lookup settled `Placement.DesktopOnly` immediately rather than needing a fresh
  argument. The table is the actual source of truth for "did the export draw a mobile screen,"
  not a guess from the feature's apparent importance.
- **A `TopLevel` destination's own gating permission and its in-screen management permission can
  differ on purpose, and the two should be decided independently.** Equipment's route is gated on
  `VIEW_MEMBERS` (viewing needs only signing in, the same reasoning as Classes) while
  `MANAGE_EQUIPMENT` gates add/log/status-change inside the screen — a *different* shape from
  Announcements, whose route itself requires `MANAGE_ANNOUNCEMENTS` because there is no
  operational reason for a coach mid-shift to read draft copy. Neither is more "correct" in
  general; the design's own reasoning for *that specific screen* decides which shape fits.
- **When two log-entry variants are told apart by which optional field is set (a report has no
  `technician`, a service record does), the derived business fact ("last service") must filter on
  that field, not on entry order or a separate type tag.** `MaintenanceLog.lastServiceOn` filters
  to entries with a technician before taking the max `occurredAt` — pinned by a test where a later
  plain report must not push the last-service date forward.
- **A new semantic status tone belongs in `:core:designsystem`'s `ChipTone` enum, not approximated
  with an existing one.** "Needs service" (amber) is neither `Positive` (green, actually working)
  nor `Critical` (red, reserved for a unit that is fully out of order) — added `ChipTone.Warning`
  rather than reuse either, since the export itself gives it a visually distinct amber badge.
- **`AnfasIcons` values can call a `private` helper (`circle()`) declared later in the same
  `object` body without a forward-reference error** — member declaration order inside a class/
  object doesn't matter for functions the way top-level property-initializer order does. Useful to
  remember before assuming new icons must go after the helpers they use.
- **A screen the export drew as a slide-in side panel can still use this app's established
  list-plus-`AnfasDialog` shape**, the same way Classes/Therapy/Announcements do for one record's
  detail — documented via KDoc as a deliberate departure (no reuse case for a one-off sliding-
  drawer primitive) rather than silently simplified.
- **Recurrence, not just occurrence, of `bug-021`-style Kotlin/Native comma-in-test-name failures**
  (see `.wolf/buglog.json` bug-069): even with the lesson already written down in
  `## Do-Not-Repeat`, a new test name with a comma slipped through because only
  `:feature:equipment:jvmTest` was run first. `./gradlew check` — which compiles the native test
  targets — is what actually catches this; a scoped `jvmTest` run is not a substitute for it
  before calling a KMP feature done.
