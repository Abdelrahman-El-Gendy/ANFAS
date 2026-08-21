# Memory

> Chronological action log. Hooks and AI append to this file automatically.
> Old sessions are consolidated by the daemon weekly.

## 2026-08-21
| 12:07 | Reconnected Stitch MCP via stdio proxy (DCR unsupported on googleapis HTTP) | ~/.claude.json | connected, 15 tools | ~8k |
| 12:07 | Extracted Stitch design: 23 screen HTML exports + design.md | design/stitch/export/, design.md | 23/23 fetched http=200 | ~45k |
| 12:07 | Derived TOKENS.md — 47 M3 colours, 4 brand accents, type ramp, radii, spacing | design/stitch/TOKENS.md | generated, 168 lines | ~15k |
| 12:07 | Implemented token layer + bundled IBM Plex Sans 400/500/600 | core/designsystem/ | ./gradlew check BUILD SUCCESSFUL | ~20k |
| 12:08 | Created ../../.claude/projects/-Users-abdelrahmanelgendy-AndroidStudioProjects-ANFAS/memory/stitch-mcp-needs-stdio-proxy.md | — | ~587 |
| 12:36 | Added Member domain type + MembershipStatus (4 states, all sourced from the export) | core/model | compiles | ~6k |
| 12:36 | MemberEntity + MemberDao + AnfasDatabase v2 with AutoMigration(1,2); schema 2.json committed | core/database | schema generated | ~8k |
| 12:36 | NEW MODULE :core:data — MemberRepository seam, Room-backed impl, Koin wiring | core/data | 6 tests pass | ~18k |
| 12:36 | 9 design-system components + hand-built icon set (material-icons unpublished past 1.7.3) | core/designsystem | compiles | ~22k |
| 12:36 | Members feature: component, state, screen, status mapping, check-in formatter | feature/members | 12 tests pass | ~25k |
| 12:36 | Routed RootComponent to MembersList (stack, not slot); App renders Children; dataModule in initKoin | composeApp | ./gradlew check + iOS OK | ~10k |
| 15:13 | Subscriptions domain: Money/Currency, SubscriptionPlan/Tier/PaymentMethod, Reminder + FailureReason, RenewalCalculator | core/model | 9 calculator tests pass | ~22k |
| 15:13 | Room v3: reminders + subscription_plans + subscriptions, AutoMigration(2,3), schema 3.json | core/database | schema committed | ~10k |
| 15:13 | ReminderRepository + SubscriptionRepository + shared StorageBoundary; plan seeding | core/data | 10 reminder tests pass | ~25k |
| 15:13 | Moved RelativeTime + added MoneyFormat to :core:common (shared by 2 features) | core/common | 12 tests pass | ~8k |
| 15:13 | 8 new design-system components + 13 icons (tabs, checkbox, dialog, callout, choice, nav) | core/designsystem | compiles all targets | ~28k |
| 15:13 | Reminder queue (tabs/filters/bulk retry/failed dialog) + renewal sheet | feature/subscriptions | 18 tests pass | ~40k |
| 15:13 | Nav shell: rail >=1024dp, bottom bar below; 3 routes with replaceAll vs push | composeApp | check + iOS OK, 60 tests | ~14k |
| 15:25 | Created core/model/src/commonMain/kotlin/com/anfas/core/model/Intake.kt | — | ~1387 |
| 15:25 | Edited core/model/src/commonMain/kotlin/com/anfas/core/model/Ids.kt | expanded (+6 lines) | ~45 |
| 15:26 | Created core/model/src/commonMain/kotlin/com/anfas/core/model/IntakeValidator.kt | — | ~1529 |
| 15:27 | Created core/model/src/commonTest/kotlin/com/anfas/core/model/IntakeValidatorTest.kt | — | ~2327 |
| 15:28 | Created core/database/src/commonMain/kotlin/com/anfas/core/database/IntakeEntity.kt | — | ~1145 |
| 15:29 | Edited core/database/src/commonMain/kotlin/com/anfas/core/database/AnfasDatabase.kt | 3→5 lines | ~34 |
| 15:29 | Edited core/database/src/commonMain/kotlin/com/anfas/core/database/AnfasDatabase.kt | 2→6 lines | ~92 |
| 15:29 | Edited core/database/src/commonMain/kotlin/com/anfas/core/database/AnfasDatabase.kt | modified subscriptionDao() | ~25 |
| 15:30 | Edited core/model/src/commonMain/kotlin/com/anfas/core/model/Member.kt | modified Member() | ~187 |
| 15:30 | Edited core/database/src/commonMain/kotlin/com/anfas/core/database/MemberEntity.kt | expanded (+12 lines) | ~251 |
| 15:31 | Edited core/database/src/commonMain/kotlin/com/anfas/core/database/MemberEntity.kt | modified observeAll() | ~144 |
| 15:31 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/MemberMappers.kt | modified toDomain() | ~298 |
| 15:31 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/MemberMappers.kt | added 1 import(s) | ~43 |
| 15:32 | Edited core/data/src/commonTest/kotlin/com/anfas/core/data/FakeMemberDao.kt | modified observeAll() | ~91 |
| 15:32 | Edited core/data/src/commonTest/kotlin/com/anfas/core/data/FakeMemberDao.kt | added optional chaining | ~120 |
| 15:33 | Created core/data/src/commonMain/kotlin/com/anfas/core/data/IntakeMappers.kt | — | ~889 |
| 15:33 | Created core/data/src/commonMain/kotlin/com/anfas/core/data/IntakeRepository.kt | — | ~644 |
| 15:34 | Created core/data/src/commonMain/kotlin/com/anfas/core/data/OfflineFirstIntakeRepository.kt | — | ~1909 |
| 15:34 | Edited core/database/src/commonMain/kotlin/com/anfas/core/database/IntakeEntity.kt | modified rowsOnce() | ~94 |
| 15:34 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/OfflineFirstIntakeRepository.kt | modified runStorage() | ~92 |
| 15:34 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/OfflineFirstIntakeRepository.kt | removed 4 lines | ~2 |
| 15:34 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/OfflineFirstIntakeRepository.kt | 7→4 lines | ~24 |
| 15:35 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/OfflineFirstIntakeRepository.kt | 5→2 lines | ~17 |
| 15:35 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/OfflineFirstIntakeRepository.kt | 2→1 lines | ~11 |
| 15:36 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/DataModule.kt | expanded (+8 lines) | ~144 |
| 15:36 | Edited core/data/src/commonMain/kotlin/com/anfas/core/data/DataModule.kt | added 1 import(s) | ~23 |
| 15:36 | Created core/data/src/commonTest/kotlin/com/anfas/core/data/FakeIntakeDao.kt | — | ~1110 |
| 15:37 | Created core/data/src/commonTest/kotlin/com/anfas/core/data/OfflineFirstIntakeRepositoryTest.kt | — | ~2759 |
| 15:45 | Edited core/data/src/commonTest/kotlin/com/anfas/core/data/FakeMemberDao.kt | 2→5 lines | ~68 |
| 15:45 | Edited core/data/src/commonTest/kotlin/com/anfas/core/data/OfflineFirstIntakeRepositoryTest.kt | 10→13 lines | ~156 |
| 15:48 | Edited core/designsystem/src/commonMain/kotlin/com/anfas/core/designsystem/AnfasIcons.kt | modified stroked() | ~470 |
| 16:20 | Created core/designsystem/src/commonMain/kotlin/com/anfas/core/designsystem/AnfasInlineEdit.kt | — | ~1277 |
| 16:20 | Created feature/intake-ocr/src/commonMain/kotlin/com/anfas/feature/intakeocr/IntakeReviewState.kt | — | ~462 |
| 16:21 | Created feature/intake-ocr/src/commonMain/kotlin/com/anfas/feature/intakeocr/IntakeReviewComponent.kt | — | ~1904 |
| 16:26 | Created feature/intake-ocr/src/commonMain/kotlin/com/anfas/feature/intakeocr/IntakeReviewScreen.kt | — | ~4727 |
| 16:26 | Edited feature/intake-ocr/src/commonMain/kotlin/com/anfas/feature/intakeocr/IntakeReviewScreen.kt | reduced (-20 lines) | ~247 |
| 16:26 | Edited feature/intake-ocr/src/commonMain/kotlin/com/anfas/feature/intakeocr/IntakeReviewScreen.kt | added 1 import(s) | ~39 |
| 16:27 | Created feature/intake-ocr/src/commonMain/kotlin/com/anfas/feature/intakeocr/IntakeOcrModule.kt | — | ~317 |
| 16:28 | Edited composeApp/src/commonMain/kotlin/com/anfas/app/navigation/RootComponent.kt | 9→7 lines | ~108 |
| 16:28 | Created feature/intake-ocr/build.gradle.kts | — | ~87 |
| 16:29 | Created feature/intake-ocr/src/commonTest/kotlin/com/anfas/feature/intakeocr/IntakeReviewComponentTest.kt | — | ~3671 |
| 16:29 | Edited feature/intake-ocr/src/commonTest/kotlin/com/anfas/feature/intakeocr/IntakeReviewComponentTest.kt | — | ~0 |
| 16:36 | Intake domain: IntakeBatch/Row/Field/Issue + OcrBounds + IntakeValidator (relational validation) | core/model | 18 validator tests pass | ~24k |
| 16:36 | Added Member.phone + members.phone_normalised index; amended v4 (uncommitted) rather than adding v5 | core/model, core/database | schema regenerated | ~9k |
| 16:36 | Room v4: intake_batches + intake_rows (@Embedded prefixes, CASCADE FK), AutoMigration(3,4) | core/database | schema 4.json | ~11k |
| 16:36 | IntakeRepository + impl: validate-on-read against live membership, import creates Members | core/data | 11 tests pass | ~26k |
| 16:36 | 5 icons + AnfasInlineEditField (3-state cell: plain/review/error) | core/designsystem | compiles all targets | ~10k |
| 16:36 | Intake review: split source/table view, pinch-zoom + drag overlay, inline edit, import | feature/intake-ocr | 12 tests pass | ~32k |
| 16:36 | Third nav destination + route wired; import navigates to the directory | composeApp | check + iOS OK, 101 tests | ~8k |
| 16:54 | Created ../../.claude/plans/plan-what-is-remaining-abundant-elephant.md | — | ~1126 |
| 17:07 | Edited ../../.claude/plans/plan-what-is-remaining-abundant-elephant.md | expanded (+39 lines) | ~670 |
| 17:08 | Edited ../../.claude/plans/plan-what-is-remaining-abundant-elephant.md | added error handling | ~1076 |
| 17:11 | Created ../../.claude/plans/plan-what-is-remaining-abundant-elephant.md | — | ~5234 |
