# Cerebrum

> OpenWolf's learning memory. Updated automatically as the AI learns from interactions.
> Do not edit manually unless correcting an error.
> Last updated: 2026-08-21

## User Preferences

<!-- How the user likes things done. Code style, tools, patterns, communication. -->

- **[2026-08-25] Every feature must be RUN on every platform before it is committed** — Android,
  iOS and desktop, not just `./gradlew check` plus a compile check. Stated directly: "i need you
  to check every feature first on each platform before commiting them." This corrects the prior
  habit of treating a green `check` plus `compileKotlinIosArm64` as sufficient and committing on
  that basis. It is a well-earned correction: the Equipment publish-guard bug and the intake
  source-photo placeholder were both invisible to the entire test suite and only visible on a
  screen. Compilation is not behaviour. `.claude/agents/feature-verifier.md` now carries this as
  a mandatory second gate, including how to handle `Placement.DesktopOnly`/`WideOnly` screens that
  are legitimately unreachable on a phone (confirm the absence; check the iPad, where the rail
  actually appears).
- **Prefers the phased, ship-it-properly loop** — implement, verify, bookkeep (`.wolf/` +
  `CLAUDE.md`), then commit with a real explanatory message. Has asked for dedicated subagents for
  the verify step and the branch/commit/push step rather than doing either inline.
- **[2026-08-25] One descriptively-named branch per feature.** Stated as "choose a convinant name
  for every branch descripting feature developed!" — a correction to having stacked Equipment, CI
  and the intake photo fix all onto `hardening/phase-3-ocr`, a branch whose name describes none of
  them. Convention now `<type>/<kebab-slug>` naming the subject, not the mechanism
  (`feature/calendar-date-picker`, `fix/intake-source-pane-on-phone`). Recorded in
  `.claude/agents/git-shipper.md`, which also now says to split work spanning several features
  into a branch+commit each rather than one vaguely-named commit.
- **[2026-08-25] Every successfully delivered feature gets merged into `main`.** Standing
  instruction ("after each successful feature delevely merge it with main too and so on"), so it
  is part of the ship step rather than something to ask about each time. Applies only to finished
  work — committed, pushed, and green on both gates. `main` had been left at the pre-hardening
  commit while 10+ phases accumulated on stacked branches, so it is normally a plain ancestor and
  `--ff-only` is the right merge; a refusal means real divergence and should be reported, not
  worked around. Encoded in `.claude/agents/git-shipper.md` step 5.
- **[2026-08-25] Wants free-text dates replaced with a real date picker** — "i need to make the
  date entered to be a date piker not a string as it is." Built as `AnfasDateField` wrapping
  Material 3's calendar. The general principle behind it: a typed date needs a parser, a parser
  can fail, and every screen then carries an "unreadable date" error path — a picker deletes all
  three. The one place free text stays correct is correcting a date read off a photographed
  sheet, where the text is a transcription and the confidence stripe is the point.
- **[2026-08-25] Also wants UI *rendering* verified per platform**, not just behaviour — "check
  the rendering ui component is properly set on each platform (ios, android and desktop)". So a
  per-platform pass covers fonts/glyphs (Arabic must not tofu), theme colours, spacing, clipping,
  RTL mirroring and breakpoint branches, not merely "the feature works".

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
- [2026-10-03] commonTest must not use JDK-21-only collection methods (`removeFirst`, `removeLast`,
  `getFirst`). On the Android host target they bind to a method the SDK lacks; thrown inside a
  component `scope.launch` the error is swallowed by `appExceptionHandler`, so a repository call
  just silently never happens (green on jvmTest, red on testAndroidHostTest). Use an Iterator.
  Also: a fresh git worktree has no `local.properties` (copy it from the main checkout), and
  `./gradlew check` runs Spotless on test sources too — run `spotlessApply` after writing tests.
- [2026-10-03] The Compose-UI jvmTest layout tests CLAUDE.md describes (AnfasScreenHeaderTest,
  IntakeReviewLayoutTest, `test-composeUi` bundle) do not exist on this branch lineage; no feature
  module has a jvmTest source set. Don't assume that infrastructure is present — check first.
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

## Key Learnings — 2026-08-25 (CI, Phase 5)

- **Xcode's autogenerated scheme lives under `xcuserdata/`, which is (correctly) gitignored, so a
  fresh checkout has *no* scheme for `xcodebuild -scheme <name>` to find** unless one was
  explicitly marked "Shared" (which writes it to `xcshareddata/xcschemes/` instead). This project's
  `.gitignore` already special-cased `!*.xcodeproj/xcshareddata/` to allow exactly this, but no
  shared scheme had ever actually been created — so CI's `ios-app` job would have failed on the
  very first run with no scheme found. The fix is a plain file copy (the scheme XML doesn't
  reference anything user-specific), not anything requiring the Xcode GUI. Check for a
  `xcshareddata/xcschemes/*.xcscheme` file before wiring any CI job that names `-scheme` — its
  absence is invisible locally because Xcode always shows *your* xcuserdata scheme too.
- **This sandbox's host OS is macOS** (`Platform: darwin` in the environment banner), so
  `xcodebuild` commands intended for a CI job can and should be run for real here rather than
  just read for plausibility — that's exactly how the missing shared-scheme gap above was caught,
  by actually running `xcodebuild build -scheme iosApp ... ` before committing the workflow that
  assumes it works.
- **A CI runner's Gradle heap override belongs in `$HOME/.gradle/gradle.properties`, written by a
  workflow step, never in the committed project `gradle.properties`.** The project's own value
  (`org.gradle.jvmargs=-Xmx8192M`) is sized for local Kotlin/Native release-framework linking
  across two iOS targets and must not be lowered project-wide just to fit a smaller CI runner.
- **Adding CI to a repo that never had a remote is gated on the remote actually existing** — this
  project's hardening plan explicitly listed "blocked on you creating the GitHub repo" for its CI
  phase. Once `git branch -vv` shows a real `[origin/...]` upstream (not just a local branch
  name), that specific gate is lifted and CI setup can proceed without asking again.

## Key Learnings — 2026-08-25 (intake source image)

- **When "continue with the next feature" has no obvious target** (all designed screens built,
  CI just shipped), don't guess — audit the persisted hardening plan against actual current repo
  state before picking something. A fork spot-checking the plan's remaining phases found most of
  it silently already done across earlier sessions (OCR capture/recognition, Android R8/signing,
  desktop and iOS release config) that the plan file itself, written when only 3 feature slices
  existed, still lists as open. The plan file is a historical snapshot, not a live task list —
  treat gaps in it as "check first," not "assume true."
- **Coil 3's `SubcomposeAsyncImage` takes named `loading`/`success`/`error` composable slots
  directly** (`coil3.compose.SingletonSubcomposeAsyncImage.kt`) — no need to reach for the
  lower-level `content: @Composable SubcomposeAsyncImageScope.() -> Unit` overload with manual
  `when (state)` branching unless you need something beyond simple per-state content. Works for
  local `file://` URIs with zero extra wiring — Coil 3's core fetchers handle local files without
  registering a network engine; `coil-networkKtor3` is only needed for http/https loading.
  `feature/intake-ocr/build.gradle.kts` already declared `libs.bundles.coil` before any code used
  it — check existing build files for already-declared-but-unused dependencies before adding one.
- **A rendered photo and hand-positioned overlay boxes must scale from the exact same rect.**
  Where overlay boxes are already positioned against a fixed assumed aspect ratio (here,
  `SHEET_ASPECT_RATIO`, an A4 stand-in with no per-photo aspect ratio stored), rendering the real
  image into that same box needs `ContentScale.FillBounds`, not `Fit` — `Fit` letterboxes the
  image at its own true aspect ratio inside the box, which visually detaches it from boxes
  positioned against the box's full extent.

## Key Learnings — 2026-08-25 (Compose layout tests)

- **A layout regression test must be proven to fail without its fix, and twice here it did not.**
  First draft asserted `gap > 0` — but the real bug left a **1dp** gap (visually flush, technically
  not overlapping), so it passed against the broken component. Second draft used the *device* width
  (402dp) instead of the width the component is actually handed (370dp, after each screen's 16dp
  page margin), where the title fits with 33dp to spare and no collision exists at all. Both drafts
  were confidently green and worthless. Revert the fix, watch it go red, restore.
- **When a layout assertion is hard to pin, probe the real numbers first.** A throwaway test that
  printed `titleRight / actionLeft / gap` across six widths turned guesswork into a table: 0dp at
  300-340dp, 1dp at 370dp, 26dp+ above. That is what revealed both mistakes and gave the 12dp
  threshold an actual basis. Delete the probe afterwards.
- **Compose UI tests on the JVM target need `compose.desktop.currentOs` on top of
  `org.jetbrains.compose.ui:ui-test` + `ui-test-junit4`.** The runner really composes and measures,
  so it needs Skiko's native renderer for the host; without it the failure is a class-load link
  error, not an assertion. Both coordinates exist at the project's `composeMultiplatform` version
  (checked against Maven Central rather than assumed).
- **Keep these on `jvmTest`, not `commonTest`.** Layout is common Compose code and identical on
  every target, so a common test would run three times for no extra coverage and pull a renderer
  into the iOS test binary.
- **`DpRect` from `getBoundsInRoot()` has no `.width` member in scope** — derive it as
  `right - left` rather than hunting for the extension import.
- **A suspiciously fast green (`BUILD SUCCESSFUL in 2s`) usually means nothing ran.** Confirm with
  `--rerun` and by reading the JUnit XML's `tests=` count, not the exit code.

## Key Learnings — 2026-08-26 (screen-level layout tests)

- **`Modifier.size` is clamped by the test surface's constraints; `requiredSize` is not.** A test
  box set to 1324dp inside `runComposeUiTest` was silently squeezed under the 1024dp breakpoint, so
  the "desktop width" case was actually exercising the *narrow* branch and passing for the wrong
  reason. The tell was that reverting the fix failed a test that had no business failing — if
  reverting breaks more branches than the bug touched, the test is measuring something else.
- **Third time this pattern has bitten: a layout test that passes against the bug.** First `gap > 0`
  when the real defect was a 1dp gap; then the device width instead of the width the component
  receives; now `size` instead of `requiredSize`. The revert-and-watch-it-fail step is not optional
  ceremony — it is the only thing that has caught any of them.
- **Check a module's existing test fakes before writing your own.** `feature/intake-ocr` already had
  `OcrFakes.kt` (`FakeTextRecogniser`, `RecordingImageStore`, `FakeCameraPermissions`) and an
  `internal FakeIntakeRepository` that revalidates on read like the real repository. Duplicating
  them is not merely wasteful, it fails to compile: `jvmTest` sees `commonTest`, and same-package
  top-level names collide across source sets even when both are `private`.
- **A thin launcher can still own a testable invariant.** `desktopApp` has no logic, but its window
  constants encode "stay above the layout breakpoint". Making them `internal` and asserting against
  `AnfasBreakpoints.tabletMax` (never a copied literal) turns a reachability rule into 4 tests that
  run in under a second with no emulator.
- **Rendering a whole screen in a test needs the component, and that is affordable.** Building a
  real `IntakeReviewComponent` took a `LifecycleRegistry`, five fakes and ~40 lines — cheaper than
  refactoring the screen to be testable, and it exercises the real composable rather than a
  simplified stand-in.


## Key Learnings — 2026-08-26 (Android instrumented smoke tests)

- **A test running against an R8-shrunk APK may only touch the app's own entry points.** The first
  `R8SmokeTest` called `runBlocking`, `GlobalContext.getOrNull()`, a `DefaultComponentContext`
  constructor and `kotlin.test`'s assertions. All five tests failed with
  `NoSuchMethodError`/`NoClassDefFoundError` — the app never calls those, so R8 correctly removed
  them. Any library API a test reaches for is by definition outside the app's reachable graph, so a
  keep rule bringing it back proves only that the keep rule works. Use `ActivityScenario` plus
  `org.junit.Assert` (Java, in the test APK) and nothing else.
- **`kotlin.test` is unusable in an androidTest against a minified app** — its asserter lookup needs
  `kotlin.collections.CollectionsKt`, which the app does not retain. `org.junit.Assert` has no
  Kotlin dependency at all.
- **`stage` shrinks but does NOT obfuscate.** AGP disables obfuscation and optimization for
  debuggable build types. Verified in `build/outputs/mapping/stage/mapping.txt`: every non-identity
  entry is `R8$$REMOVED$$CLASS$$n`, a deletion; zero renames. So `stage` cannot test a rename
  hazard — don't write a test claiming it does.
- **`ActivityScenario.recreate()` is the right way to test the Config-discriminator hazard**: a real
  save-then-restore inside the minified app exercises Decompose's `StateKeeper` and the serializers
  for real, instead of asserting about serial names. Pair it with an activity-identity assertion or
  the test is vacuous if `recreate()` no-ops.
- **AGP consistent resolution pins androidTest to the production runtime's versions.** Declaring
  `libs.kotlinx.serialization.json` (catalog 1.11.0) on `androidTestImplementation` failed to
  resolve, because `stageRuntimeClasspath` resolves `{strictly 1.8.0}` — the Android classpath never
  asks for 1.11.0 and the transitively-supplied kotlinx BOM pins 1.8.0. Don't add a version to an
  androidTest configuration; design the test not to need the dependency.
- **Minifying the app means the test APK is minified too, and that needs two extra rule files** —
  `testProguardFiles` for `-dontwarn com.google.errorprone.annotations.**`, and a build-type-scoped
  file on `stage` for `-keep class kotlin.LazyKt*`. Never put either in `proguard-rules.pro`:
  a production keep rule motivated by a test is one nobody can later justify. Prove the scoping —
  `release`'s mapping still shows `kotlin.LazyKt__LazyKt -> R8$$REMOVED$$CLASS$$766`.
- **`-dontshrink` in `testProguardFiles` does not solve a missing stdlib class.** Shared
  dependencies ship in the app APK only, so stdlib is never the test APK's program input; the keep
  rule has to be on the app side.
- Emulator installs need `-Pandroid.injected.build.abi=arm64-v8a` on this machine (93% full /data).

## Key Learnings — 2026-08-26 (desktop window geometry)

- **`WindowState.position` stays `WindowPosition.PlatformDefault` when the platform placed the
  window.** So a `snapshotFlow { windowState.toGeometry() }` that requires a specified position
  emits nothing on first run and saves nothing at all. Read `window.x/y/width/height` off the AWT
  frame instead (inside `FrameWindowScope`), via a `ComponentAdapter` for moves/resizes plus one
  direct read for the initial placement, which fires no event.
- **AWT `window.*` and `GraphicsConfiguration.getBounds()` are in the same logical user-space
  units**, so reading geometry from the frame removes the dp-vs-px question entirely. On this
  machine the screen is 1352x878 logical at scaleX=2.0.
- **`defaults read com.apple.java.util.prefs` and plistlib both serve a stale cfprefsd cache** — a
  key written seconds ago reads as absent. Verify `java.util.prefs` from a fresh JVM
  (`Preferences.userRoot().node("...").keys()`). This cost a wrong "the write isn't happening"
  diagnosis.
- **macOS pulls a window fully on-screen itself** if the restored bounds overhang. A restore test
  seeded with x+width > screen width will come back repositioned — that is the OS, not a bug in the
  resolver. Seed a geometry that actually fits when testing exact round-trip.
- **An overlap gate and a position clamp must not use the same threshold**, or the clamp is
  unreachable dead code and every edge-parked window gets recentred. Gate on "any overlap at all";
  clamp to the grabbable minimum. A test caught this, not review.
- **The packaged app is where `java.prefs` can be missing.** Check the jlink module list in
  `ANFAS.app/Contents/runtime/Contents/Home/release` — the `bin/java` binary is not in the app
  image, so `--list-modules` cannot be run against it.
- Window geometry, logging, crash handling, the EDT dance and shutdown ordering are all legitimate
  `desktopApp` content — they have no Android/iOS counterpart. "The launcher holds no logic" means
  no *feature* logic.

## Do-Not-Repeat — 2026-08-26

- **Do not take whole-screen `screencapture` shots to verify a desktop window.** Without
  Accessibility permission the ANFAS window cannot be raised, so the capture shows whichever of the
  user's apps has focus — this session captured their Outlook calendar, work timesheet and browser
  tabs before I stopped. Verify desktop geometry numerically (seed prefs, launch, read back from a
  fresh JVM) and say plainly that a content screenshot was not possible.

## Key Learnings — 2026-08-26 (keyboard / IME handling)

- **`enableEdgeToEdge()` requires `android:windowSoftInputMode="adjustResize"` in the manifest.**
  Without it the system picks adjustPan and slides the whole window up — the app's own top bar ends
  up under the status bar. If chrome that should be fixed is moving when the keyboard opens, that is
  adjustPan.
- **`imePadding()` on a child that a parent centres is wrong.** Padding makes the child taller and
  centring splits the difference, so content rises by half the keyboard height. Put the inset on the
  container so the space the child is centred in shrinks.
- **Compose does not re-run bring-into-view when the keyboard resizes the viewport.** It scrolls on
  focus arrival, before the keyboard is up. Fix: a `BringIntoViewRequester` with a `LaunchedEffect`
  keyed on `WindowInsets.ime.getBottom(density)` as well as focus. This was the actual fix; the
  container/inset changes were necessary but not sufficient.
- **`KeyboardActions(onNext = { maybeNull?.invoke() })` disables the keyboard's Next key.** A
  supplied handler replaces the platform default even when its body does nothing. Pass null to keep
  the default (advance focus / dismiss).
- **A non-zero `WindowInsets.ime` does not prove `imePadding()` is applying it.** Print the raw
  inset in a temporary `Text` to separate "inset not reported" from "inset consumed by an ancestor"
  before touching layout. I changed `App.kt` on the consumption hunch, found it made no difference,
  and reverted it — `windowInsetsPadding(x.only(sides))` does limit its consumption correctly.
- **Test the keyboard in landscape.** Portrait had enough slack to mask two of three bugs; landscape
  leaves ~80dp of form area on a phone, which is where every mistake shows.
- `Dialog` opens its own window and ignores the constraints of whatever composes it, so a dialog's
  internal layout cannot be measured through the public composable. Split an `internal` panel
  composable out and test that.

## Do-Not-Repeat — 2026-08-26 (second entry)

- **Do not conclude an inset is being consumed without measuring it.** I edited `App.kt` to swap
  `safeDrawing` for `systemBars.union(displayCutout)` on the theory that safeDrawing's IME component
  was being consumed, wrote a confident comment saying so, and it changed nothing — the real cause
  was the missing bring-into-view. Reverted. Measure first, then edit.

## Key Learnings — 2026-08-26 (session validity)

- **A persisted session must be re-derived from the database on every emission, not trusted as
  stored.** `Settings` (SharedPreferences / NSUserDefaults) survives backup and device transfer; the
  Room file is deliberately excluded because the domain is PII. So a transferred install holds a
  session id for a staff row that does not exist, and reading the store alone showed the dashboard to
  an authenticated nobody. Reproduced on Android by writing only the two session keys into
  `shared_prefs/anfas.xml` with no `databases` dir.
- **Stored roles go stale and that is a privilege escalation.** A demoted Owner kept Owner
  permissions until sign-out, because the session's role set was a snapshot. Derive roles from the
  row.
- **Do not clear storage from inside a cold flow** to "self-heal" a bad session: it fires per
  collector, and one transient failure becomes a permanent sign-out. Emit null instead.
- **AGP 9 writes the debug APK to `build/intermediates/apk/debug/`, and
  `build/outputs/apk/debug/` can hold a stale artifact from an older build.** I side-loaded a
  day-old APK and concluded a working fix had failed. Check the APK's mtime against the source, or
  install via `installDebug` rather than a hand-picked path.
- **The emulator's `/data` fills up.** `adb shell pm trim-caches 2000M` is the safe way to free space
  (caches only, apps regenerate them) — never uninstall the user's other apps.
- To create a "signed in" state without driving the UI: write `session.user_id` and `session.roles`
  into `shared_prefs/anfas.xml` via `run-as com.anfas.app`. No password hash needed, because sign-in
  is not involved.

## Do-Not-Repeat — 2026-08-26 (third entry)

- **Backtick test names must not contain commas** — Kotlin/Native fails with "Name contains illegal
  characters". This is the third time (bug-069, and again here with `roles come from the staff row,
  not from the stored session`). Only `compileTestKotlinIosSimulatorArm64` catches it, so `check`
  must be run before shipping, not just the JVM tests.

## Key Learnings — 2026-08-26 (instrumented tests in CI)

- **A GitHub ubuntu runner needs a KVM udev rule before the Android emulator is usable.** `/dev/kvm`
  exists but is not accessible to the runner user; without the rule the emulator either crawls or
  times out. This is required setup, not tuning.
- **`google_atd` / API 35 / x86_64 is the CI image of choice.** ATD = Automated Test Device, stripped
  of apps a CI run never touches, so it boots far faster. Google APIs variant chosen so nothing rests
  on whether ML Kit's bundled model needs Play Services — the app initialises ML Kit via
  ComponentDiscovery at startup, which is what the R8 tests check.
- **Never pass `-Pandroid.injected.build.abi=arm64-v8a` in CI.** Locally it exists only to fit a
  nearly-full emulator disk; on an x86_64 runner it strips the libraries the runner needs and the
  failure looks like a shrinking bug.
- `system-images;android-36;{aosp_atd,google_atd,google_apis};x86_64` all exist — check with
  `sdkmanager --list | grep system-images` before guessing an image spec.
- **An action's input names can be verified without running CI**: fetch its `action.yml` from the
  raw GitHub URL and diff the declared inputs against the ones used. Cheap, and catches the silent
  failure mode where an unknown input is ignored.
- What still cannot be verified locally is the runner/emulator combination itself. Same class of risk
  as the macos-15 → macos-26 discovery: the first real CI run is the test.

## Key Learnings — 2026-08-26 (WhatsApp Phase 1)

- **A deterministic id can replace a dedupe query entirely.** `SubscriptionPlanSeed` already stated
  the pattern ("an upsert keyed by a stable id, so re-running it cannot duplicate rows"). Applying it
  to reminders (`renewal:<termId>`) removed two proposed columns — an idempotency key and a content
  key — because the id is both.
- **But `@Upsert` replaces every column by id**, so a scheduler must NOT upsert over existing rows:
  it would reset a FAILED row to QUEUED and wipe its attempts. Add a `SELECT id ... WHERE id IN (:ids)`
  query and insert only the missing ones. DAO-only change, no migration.
- **Room refuses to build when a new NOT NULL column has no `defaultValue`** — "New NOT NULL column
  added with no default value specified". So KSP guards presence; only a test can guard the *value*.
  This repo's first added column (every earlier migration added/dropped whole tables).
- **`stateIn(WhileSubscribed)` means `state.value` is the initial value until something collects.**
  A component guard reading `state.value.mayX` therefore refuses until subscribed — fail-safe, but
  tests must subscribe (Turbine) before acting, or they test the initial value instead.
- **Seeding an Android app's database from the host:** the schema lives in the `-wal` until
  checkpointed, so pulling only `anfas.db` gives a 4KB empty file. Pull `.db`, `-wal` and `-shm`
  together, edit with python sqlite3, `PRAGMA wal_checkpoint(TRUNCATE)`, then push back and delete the
  device's `-wal`/`-shm`. No `sqlite3` binary exists on the emulator.
- **`uiautomator dump` beats eyeballing screenshots for finding tap targets** in Compose: clickable
  nodes appear with real `bounds`, which both locates them and proves clickability. It is how I
  confirmed the renewal tile had actually become clickable.
- Found while verifying: the reminder queue's only phone entry point was the *failed reminders* tile,
  gated on `failedReminders > 0` — so it was unreachable exactly when empty. CLAUDE.md had already
  claimed the renewal tile was the route; now it is.

## Key Learnings — 2026-08-26 (WhatsApp Phase 2)

- **A "not configured" flag on an integration seam is worth more than a hidden button.** Binding a
  null gateway with `isConfigured = false` lets the UI explain itself, and stops a run from marking
  every row FAILED and spending its attempts before the integration exists. An absent Koin binding
  would instead crash on first use.
- **Distinguish "rejected" from "unreachable".** Unreachable means we do not know whether it sent,
  which is precisely when an idempotency key earns its place. Same row outcome, different meaning.
- **Increment the attempt counter BEFORE the call.** A crash mid-send otherwise leaves the row
  looking untried and it is retried forever.
- **Phone normalisation cannot be told apart by prefix alone.** Without a leading 0 or 00, only
  length distinguishes a foreign international number from a local one missing its trunk zero.
  Prepending the default country code unconditionally silently redirects every foreign number to a
  stranger. My own test caught this.
- **Backtick test names with commas fail Kotlin/Native only in `commonTest`.** `jvmTest` names may
  keep their commas (DesktopDataDirTest has two, legitimately) — Kotlin/Native never compiles them.
  That sharpens the rule I had been over-generalising. `./gradlew check` catches it; running only
  `jvmTest` does not.
- **Making a parked feature real turns its placeholder copy into a lie.** The queue's empty state
  promised "once the daily job schedules them"; there is no daily job. Re-read the copy of any
  screen a feature newly populates.
- To exercise a `isConfigured = true` branch on a device without shipping a fake: flip the null
  object temporarily, install, verify, revert. Same falsification discipline as reverting a fix.

## Key Learnings — 2026-08-26 (queue table on a phone)

- **`fillMaxHeight()` on a leading-edge stripe needs `Modifier.height(IntrinsicSize.Min)` on the
  parent Row.** In a wrap-content Row there is no height to fill, so it silently resolves to zero
  and the stripe disappears. Copying the construct from a row that sets its own height does not
  carry the height with it.
- **`dataMonoLtr` on `scheduledLabel()` was a latent RTL bug in the table**, invisible until the
  queue held a reminder old enough to print an absolute date rather than "Today". Arabic rendered
  `17 أغسطس 2026` as `أغسطس 17 2026`. The rule is about the *string*, not the screen: a localised
  date is never a Latin-only run, however numeric it looks.
- **A layout test's boundary case must be stated in the width the component receives.** Asserting
  the table appears at exactly `AnfasTableMinWidth` of *window* failed, because the screen's own
  horizontal padding means the table is handed less. I turned the failure into the assertion — the
  padding effect is now documented by a passing test rather than lying in wait.
- **Falsify a responsive branch in both directions.** Forcing always-table reddened the two card
  tests; forcing always-cards reddened the wide test. Neither alone proves the branch is real.
- Reuse the module's `TestDoubles.kt`: promoting file-private fakes to `internal` there is what lets
  a `jvmTest` layout test use them, instead of declaring rivals that collide across source sets.
