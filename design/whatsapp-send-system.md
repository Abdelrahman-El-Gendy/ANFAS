# The WhatsApp send system — design

**Status: on paper. Nothing here is implemented.** Written 2026-08-26 against `main` at `18a834a`.

This is the largest single gap in ANFAS. The queue, the failure taxonomy, the retry rule and the
staff worklist are all real and tested; nothing sends. This document decides how sending should
work, what can be built before the Meta account exists, and what is deliberately deferred.

---

## 1. What is already built

Worth stating precisely, because the design's job is to fill gaps rather than restate the model.

| Piece | Where | State |
|---|---|---|
| `Reminder` — id, memberId, denormalised name+phone, template, scheduledAt, attempts, status, failure | `core/model/…/Reminder.kt` | Complete |
| `ReminderStatus` — `QUEUED` / `SENT` / `FAILED` | same | Complete |
| `ReminderTemplate` — `reminder_ar`, `reminder_en`, `payment_due`, `marketing_promo`, each with a `TemplateLanguage` | same | Names only; no parameter definitions |
| `FailureReason` × 5, each carrying `isRetryable` | same | Complete, and `RATE_LIMITED` is the only retryable one |
| `ReminderFailure` — reason, `providerCode`, lastAttemptAt, detail | same | Complete |
| `Reminder.canRetry` | same | Complete |
| `reminders` table with failure columns | `core/database/…/ReminderEntity.kt` | Complete |
| `ReminderRepository` — `observeQueue`, `observeCounts`, `observeReminder`, `retry(ids)`, `upsert(list)` | `core/data/…/ReminderRepository.kt` | `retry` already skips non-retryable rows; **`upsert` has no production caller** |
| Queue screen with tabs, search, template filter, bulk select; failed-reminder dialog | `feature/subscriptions/…` | Complete, minus the two dead controls it documents |
| `VIEW_REMINDERS`, `RETRY_REMINDERS` | `core/auth/…/Permission.kt` | Complete |
| `RenewalQueue` + `TermProgress.EXPIRING_SOON_DAYS = 7` | `core/model/…` | Complete — this is the source of *who* needs reminding |
| Ktor client factory with all three engines wired | `core/network/…/HttpClientFactory.kt` | **Intentionally inert**, and `:composeApp` deliberately does not depend on it |
| `:server` | `server/…/Application.kt` | A `/health` endpoint and nothing else |

So the missing pieces are: something that **creates** reminders, something that **sends** them, a
place to keep **credentials**, a record of **consent**, and a definition of what "sent" means.

---

## 2. The decision that determines everything else: where the send happens

Meta's Cloud API is a single `POST` with a bearer token. The question is which machine holds the
token.

### Option A — device sends directly to Meta

Simplest: `:core:network` gains a client, the device posts to `graph.facebook.com`, done. No
hosting, no server work.

**Why this is wrong as a default.** A WhatsApp Business permanent access token grants send rights
for the entire business number. On a reception device it is one rooted phone, one shared tablet, or
one departing employee away from someone being able to send as the gym to any number they like.
`Settings` is unencrypted on every platform this app targets — a fact CLAUDE.md already leans on to
justify keeping a *staff id* there rather than a token. A Meta token is exactly the secret that rule
exists to keep off the device.

Two further problems, both structural rather than security: two receptionists both pressing "Run
queue" produce duplicate messages to the same member, because each device has its own database and
neither knows the other sent; and Meta's rate limits are per phone number, so per-device backoff
cannot honour them.

### Option B — a thin relay on `:server`, device stays the source of truth

The device computes the queue from its own data and posts *send requests* to our own server; the
server holds the Meta token and forwards to Meta.

- The token never reaches a device.
- Rate limiting and backoff live where they can see all traffic for the number.
- Cross-device duplicate suppression becomes possible (§6).
- **Crucially, it does not require the sync layer.** The server never needs member or term data —
  it receives "send template X to number Y with parameters Z" and relays it. This is the insight
  that unblocks WhatsApp from the sync project, which is otherwise its prerequisite.

Cost: the gym must host something, and the device must authenticate to it.

### Option C — server owns the queue and runs a real schedule

The honest end state, and what the export's "Daily job last ran 06:00 today" line implies. It needs
the server to know members and terms, i.e. the sync layer.

### Recommendation

**Build Option B.** It is the only one that keeps the token off shared devices while remaining
achievable without sync, and it is a strict subset of Option C — the relay endpoint survives
unchanged when the server later grows its own scheduler.

Option A is acceptable **only** as a single-device pilot on a device the owner physically controls,
and if it is ever used the token must live behind the platform keystore (Android Keystore /
iOS Keychain), not `Settings`. That is a deliberate, documented downgrade, not a shortcut.

---

## 3. Staging: what is buildable before a Meta account exists

Almost all of it, which is the useful finding.

**Phase 1 — enqueue (no account needed, no network).** Nothing today creates a `Reminder`.
A `ReminderScheduler` in `:core:data` materialises the queue from `RenewalQueue`: for each member
whose term has expired or expires within `EXPIRING_SOON_DAYS`, and who has consented (§5), and who
has no `QUEUED` or `SENT` reminder for the same template within the dedupe window, insert a
`QUEUED` row. This is pure logic over existing data, fully unit-testable, and immediately visible
in the existing queue screen. It makes the queue stop being empty.

**Phase 2 — the relay contract (no account needed).** Define and implement the endpoint on
`:server` plus the client in `:core:network`, with a `FakeWhatsAppGateway` that returns scripted
successes and failures. Every code path — backoff, attempt counting, failure mapping, idempotency —
is exercised against the fake. This is where the "Run queue" and "Send test message" controls the
queue screen currently omits become real.

**Phase 3 — real credentials (needs the Meta account).** Swap the fake for the live gateway.
Templates must be submitted to Meta for approval before they can be sent, which is a lead time
measured in days, not minutes.

**Phase 4 — unattended scheduling and delivery receipts (needs sync + webhooks).** Deferred; §8.

Phases 1 and 2 are real, shippable work that does not wait on you.

---

## 4. Data model changes

Five additions. Each needs a Room migration and a committed schema, per the project's rule that
schemas in `core/database/schemas/` are never deleted.

**On `Member`:**

- `whatsappOptIn: Boolean` — consent to receive template messages. Default `false`. See §5.
- `preferredLanguage: TemplateLanguage?` — decides `reminder_ar` vs `reminder_en`. Null means fall
  back to the gym default (Arabic, for an Egyptian gym). The app's *UI* language is a device
  setting and must not be used for this: the receptionist's interface language says nothing about
  what the member reads.

**On `Reminder`:**

- `idempotencyKey: String` — see §6. In practice the `ReminderId`, stored explicitly so the
  contract is visible rather than implied.
- `templateParameters: String` — the resolved positional parameters, as JSON. Stored rather than
  recomputed at send time, so what was sent is auditable and a retry sends the identical message
  rather than one recomputed from data that has since changed.

**New table `reminder_runs`:** `id`, `startedAt`, `finishedAt?`, `requested`, `sent`, `failed`,
`triggeredByStaffId`. This is the source of truth the export's "Daily job last ran…" line needs,
and it is what makes a run auditable — "who sent 200 marketing messages on Friday" is a question
that will be asked.

---

## 5. Consent, and the one thing Meta will not tell you

`FailureReason.NOT_OPTED_IN` already exists, which implies the app knows who has opted in. It does
not, and **there is no API to ask.** Opt-in is a business obligation the business records itself.

So consent must be captured, not queried:

- A checkbox on the member profile and on the intake sheet review: *"May we message this member on
  WhatsApp?"*. This is the only honest source.
- A `NOT_OPTED_IN` failure from Meta **writes back** — it sets `whatsappOptIn = false` on the
  member, so the queue self-corrects and stops re-attempting a number that will never accept
  messages. This is the one place a send failure is allowed to mutate member data, and it is worth
  the exception: without it, every run re-fails on the same rows forever.

The scheduler must skip members without consent rather than enqueue-and-fail. Enqueuing a message
that is certain to fail turns the Failed tab into noise, and the Failed tab is meant to be a
worklist.

**`MARKETING_PROMO` is a different legal category from the other three.** Meta separates *utility*
templates (a renewal reminder about an existing relationship) from *marketing* ones. Marketing
messages cost more, are throttled harder, and are subject to opt-out obligations — a member who
replies "stop" must stop receiving them. Since there is no inbound message handling (no webhook),
**marketing sends cannot honour an opt-out and should stay disabled until webhooks exist.** The
template can remain in the enum; the scheduler must not select it.

---

## 6. The send path

For each `QUEUED` reminder, oldest `scheduledAt` first, one at a time:

1. **Resolve the recipient number to E.164.** A concrete gap: `IntakeValidator.normalisePhone`
   deliberately folds to the *local* Egyptian form (`+20 100 111 2222` → `01001112222`) because it
   exists for duplicate detection. Meta wants international digits with no `+` (`201001112222`).
   This needs a separate `toE164()` — not a change to `normalisePhone`, whose local form is load-
   bearing for the duplicate-detection it was written for.
2. **Send with an idempotency key.** Meta does not deduplicate. If the HTTP call times out after
   Meta accepted it, a naive retry sends twice — and a member receiving the same renewal notice
   twice is exactly the sort of thing that makes a gym stop using the app. The relay keeps recently
   seen keys (a bounded window, hours not days) and returns the original outcome for a repeat.
   The relay can therefore also suppress the two-receptionists case in §2, because both devices
   derive the same key from the same `ReminderId` only if they share the row — which they do not.
   **So cross-device suppression needs a second key**: a content key of
   `(memberId, template, scheduledAt-day)`, which both devices *do* compute identically. Both keys
   are needed and they do different jobs — one guards a retried HTTP call, the other guards two
   devices doing the same day's run.
3. **Increment `attempts` before the call, not after.** A crash mid-send must not leave a row
   looking untried, or it will be retried forever.
4. **On success:** status `SENT`. On failure: status `FAILED`, mapped reason, `providerCode`,
   `lastAttemptAt`, and the provider's raw text into `detail` — which the existing failed-reminder
   dialog already renders.
5. **Backoff on `RATE_LIMITED` only**, exponential with jitter, and a hard attempt ceiling
   (4 is a reasonable start) after which the row stays `FAILED` and stops consuming runs. The
   existing `retry()` already refuses non-retryable rows, so the manual Retry button needs no
   change.

**Throughput.** Meta's messaging limits are per business number and tiered (typically starting at
1,000 unique recipients per rolling 24 hours, rising with quality rating). A gym's renewal queue
will be tens of messages, so the tier is not a practical constraint — but the per-second throughput
is, so sends are serial with a small delay rather than a parallel fan-out. Serial also keeps the
queue screen's live counts legible as a run progresses.

---

## 7. Mapping Meta's errors onto `FailureReason`

The existing taxonomy anticipated this well. A starting mapping:

| Meta condition | `FailureReason` |
|---|---|
| Re-engagement / outside allowed window (the `131047` the model already cites) | `NOT_OPTED_IN` |
| Recipient number invalid or undeliverable | `INVALID_PHONE_NUMBER` |
| Rate limit or throughput exceeded | `RATE_LIMITED` |
| Template paused, disabled, or quality-blocked | `TEMPLATE_PAUSED` |
| Template not found, or parameter count mismatch | `UNKNOWN` — with the raw text in `detail` |
| Anything unrecognised | `UNKNOWN` |

**Confirm every code against the API version pinned at implementation time.** Meta changes these,
and this table is a starting point rather than a specification. The mapping belongs in one function
with a test per row, so a wrong guess is a one-line fix rather than a hunt.

A template-parameter mismatch mapping to `UNKNOWN` is unsatisfying — it is a developer error, not
an operational one, and it will look like a mystery to reception. If it ever occurs in practice it
earns its own `FailureReason` with copy aimed at whoever maintains the templates.

---

## 8. What "sent" means, and what stays unbuilt

**`SENT` means Meta accepted the message, not that anyone received it.** Delivery and read receipts
arrive only through webhooks, which need a publicly reachable HTTPS endpoint. Until that exists:

- **No `DELIVERED` or `READ` status.** Adding either without webhooks would put a tab in the UI
  that cannot be populated — the same reason the offline banner is not wired and
  `staff-dashboard` was declined.
- The queue screen should say what `SENT` means where staff can see it. "Sent" reading as
  "delivered" is the kind of quiet mismatch that gets someone told a member was notified when they
  were not.

Also deliberately out of scope:

- **Unattended scheduling.** Without a server that knows the data, a "daily 06:00 run" depends on a
  device being awake, and two devices would double-run. Phase 1's run is staff-triggered, and the
  export's "last ran" line stays unbuilt until Option C. `reminder_runs` records real runs in the
  meantime, so the line becomes true rather than invented the day it can be.
- **Inbound messages**, and therefore automatic opt-in capture and opt-out handling.
- **Marketing sends** (§5).

---

## 9. Security and privacy

- **The token lives on the server, in its environment, never in the repo** — the same rule
  `keystore.properties` and `Local.xcconfig` already follow.
- **The device authenticates to the relay.** Simplest workable scheme: a per-installation
  credential issued by the owner, revocable server-side, so a lost tablet is cut off without
  rotating the Meta token. This is a smaller problem than general auth because there is exactly one
  endpoint and one caller.
- **Log no member data.** `OfflineFirstAuthRepository` already logs "Staff signed in" with no
  username, on the reasoning that a shared reception device must not keep a record of who did what
  on disk. The same applies harder here: log reminder ids and outcome counts, never names, numbers
  or message bodies. Ktor's `Logging` plugin is currently `LogLevel.HEADERS`; the send path must
  keep it there or below, because `BODY` would write member phone numbers and names into the log.
- Phone numbers are already PII under the project's own rule that excludes the database from cloud
  backup. Anything the relay persists must respect that.

---

## 10. Permissions

`VIEW_REMINDERS` and `RETRY_REMINDERS` exist. Sending needs a third:

- **`SEND_REMINDERS`** — triggering a run. Distinct from `RETRY_REMINDERS`, because retrying one
  failed message a member is expecting is a different act from dispatching two hundred. Reception
  should plausibly hold both; the split exists so that decision is available rather than assumed.

`NavigationPermissionTest`'s existing rule applies: any role holding `SEND_REMINDERS` must also
hold `VIEW_REMINDERS`, or it is a permission with no screen to use it from.

---

## 11. Testing

The pattern this project already uses, applied here:

- **`ReminderScheduler`** — pure logic over `RenewalQueue`, so ordinary unit tests: consent
  respected, dedupe window honoured, expired-before-expiring ordering, marketing never selected.
- **The gateway is an interface with a fake**, the same shape as `CameraPermissions` and
  `AppDispatchers` — because a real HTTP call cannot have its rate-limit branch exercised on
  demand. The fake scripts each `FailureReason` in turn.
- **The error mapping gets a test per row** of §7's table.
- **Idempotency gets a test that sends the same key twice** and asserts one message.
- **`toE164()` gets the awkward inputs**: local `01…`, already-international `+20…`, Arabic-Indic
  digits (`foldDigitsToAscii` matters here too), and a number too short to be valid.
- Migration test, as every schema change here has had.

---

## 12. What this needs from you

1. **A Meta WhatsApp Business account** — a Business Manager, a verified business, a phone number
   dedicated to the API (it cannot also be used in the normal WhatsApp app), and a payment method.
2. **Template approval.** The four templates in `ReminderTemplate` must be submitted and approved,
   in Arabic and English, with their parameter lists fixed. Their current names are placeholders
   until Meta approves the real ones. This has a lead time.
3. **Where the relay runs.** Any small always-on host. Without it, the honest options are the
   Option A pilot or nothing.
4. **Two product answers:** is marketing messaging wanted at all (it changes the consent and
   opt-out obligations), and what is the default language for a member whose preference is unknown
   — presumably Arabic, but a gym with an expat clientele may differ.

---

## 13. Recommended order

1. `ReminderScheduler` + consent fields + migration. No account, no network, and the queue stops
   being empty. **Start here.**
2. Gateway interface + fake + error mapping + idempotency + backoff. Wires "Run queue" and "Send
   test message" against the fake.
3. `:server` relay endpoint and device credential.
4. Live credentials, template approval, a pilot on one number.
5. Webhooks, then delivery receipts, opt-out, marketing, and an unattended schedule — in that order,
   because each depends on the one before.
