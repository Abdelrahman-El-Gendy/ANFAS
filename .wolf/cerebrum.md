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

