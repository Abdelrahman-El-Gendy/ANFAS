# The sync layer — design

**Status: on paper. Nothing here is implemented.** Written 2026-08-27 against `main` at `7bb499e`.

Sync is the system the most other work is waiting on. `sync-conflict` and `offline-banner` are built
and tested with zero callers; the Room file is deliberately excluded from cloud backup because the
whole domain is PII, so **today a dropped tablet is a dead gym**. This decides how sync should work,
what to build first, and — at some length — what not to sync at all.

---

## 1. Three things I had wrong

Stated first because two of them were in my own earlier summaries, and a reader who checks them would
rightly discount everything after.

**Sync does not unblock desktop OCR.** `core/ocr/src/jvmMain/.../Ocr.jvm.kt` gives three reasons the
desktop has none, weakest to strongest: there is no viable pure-JVM engine (Tess4J means JNI natives
plus tens of megabytes of trained data in every Dmg/Msi/Deb); a file-picker *without* recognition
would be worse than nothing; and "decisively: the database is a local file per device with no sync
yet". Sync removes only the third. What it unblocks is desktop **review** of a sheet photographed on
a phone — which that same file endorses ("the wide 40/60 review pane, which is genuinely the best
place to review a sheet once sync exists"). That matters for planning, because it puts the headline
benefit on the **blob channel** (§8), the riskiest part of this design.

**A device-written timestamp cannot be the protocol cursor.** Two tablets on gym wifi drift by
minutes; a factory-reset device is off by years. A device whose clock runs behind writes rows the
puller has already passed, and those rows are *never delivered again* — silent, invisible,
unreproducible loss. The cursor must be a **server-assigned monotonic sequence**. `updated_at` is
useful for showing a human when something changed, and for nothing else.

**"Server-authoritative" is only true for convergence, never for admission.** Every write path is an
`OfflineFirst*` repository that commits to Room and returns `AppResult.Success` before any network
exists, and the UI observes Room `Flow`s. `OfflineFirstCheckInRepository` decides GRANTED/DENIED from
the *local* subscription table and has already let someone through the door. The server can decide
which value survives; it cannot decide whether a write was allowed. Designing 409-rejecting endpoints
without internalising that produces error paths no client can act on.

---

## 2. What exists, and the shape of the gap

| | State |
|---|---|
| `MemberConflict` — compares 4 fields, tested | Built, no caller. KDoc: *"There is no sync yet."* |
| `AnfasConflictRow` / `AnfasConflictHeader` | Built, no caller |
| `syncConflictMessage` | *"N fields differ between this device and **the server**. Choose which copy to keep."* |
| `AnfasBanner` + `offlineTitle` | *"Working offline — data is stored on this device"* — deliberately **not** promising sync |
| `:core:network` — Ktor, OkHttp/Darwin/CIO wired per target | Intentionally inert; nothing depends on it |
| `:server` | A `/health` route and `install(Authentication)` with no providers |
| Change tracking | **None.** No `updated_at`, `version`, `dirty` or `deleted` column on any of the 14 tables |
| Device identity | **None.** Zero hits for `deviceId` / `installationId` |

The conflict copy is worth dwelling on: it already commits to **device-vs-server comparison with a
human choosing**, and `MemberConflict` deliberately excludes `lastCheckInAt` because *"a check-in is
an append-only event, so the later timestamp is simply the truth"*. That is a sound instinct and this
design keeps it.

The gap is bigger than it looks. Only three tables carry `created_at`, and all three are mutated by
read-modify-`@Upsert` without touching it — so **no table today can answer "what changed since X"**.

---

## 3. Change tracking

Three pieces, and two of them are deliberately *not* the obvious thing.

### An outbox, not a dirty flag

`sync_outbox(seq INTEGER PRIMARY KEY AUTOINCREMENT, table_name, row_id, op, base_version, captured_at)`,
appended in the **same transaction** as every write.

A dirty flag says a row changed; a push needs to say *which version it was changed from*, and nothing
today carries that — `MemberDao.upsertAll(members)` takes whole domain-built rows with no memory of
what was read. The outbox is also what makes `AnfasBanner` honest for the first time: "changes will
sync when you reconnect" becomes a promise the app can keep, because there is a queue to point at.

### Hard local deletes plus a tombstone table, not a `deleted` column

`sync_tombstones(table_name, row_id, deleted_at, device_id)`.

A `deleted` column on 14 tables is the intuitive choice and it is wrong here, for three separate
reasons:

- **SQLite does not cascade an UPDATE.** There are four FKs, all `ON DELETE CASCADE`, including the
  two-deep `members → therapy_cases → therapy_sessions`. Flagging a member leaves the clinical
  narrative live and un-flagged, pointing at a tombstone — invisibly, because nothing filters on the
  flag. The alternative is hand-writing a two-deep transactional cascade on device *and* server and
  keeping them agreeing, which would be the highest-risk code in the project.
- **It would need `AND deleted = 0` in roughly forty existing queries.** Miss one and tombstoned
  members reappear in the directory; miss `observeNormalisedPhones` and a deleted member's phone
  number permanently blocks re-registering that person through OCR intake.
- **It would kill a state the app already has.** `MemberProfileState.Missing` exists precisely
  because `observeMember` emits null when the row is gone, and its KDoc already anticipates sync: *"a
  member deleted on another device should say so rather than render an empty shell of a profile."*
  Soft-delete and that deliberate state becomes dead code.

With a separate table, native CASCADE keeps working. The tombstone writer reads the child ids inside
the transaction *before* deleting — a two-level read rather than a reimplemented cascade.
`check_ins`, being append-only, needs no tombstones at all.

### A server-assigned sequence as the cursor

Pull is `GET /sync/changes?since=<server_seq>`. The server stamps every accepted row with the next
value of its own monotonic counter; the device stores the highest it has applied. No device clock
enters the protocol.

Two things the protocol must get right that are easy to miss:

- **An applied page needs topological ordering, not sequence ordering.** Room enables `foreign_keys`
  per connection, so `intake_rows` arriving before their `intake_batches` parent — entirely possible
  across a page boundary — is a constraint violation that aborts the whole pull transaction.
- **Pull must be idempotent under re-delivery** (a retried page, a cursor rewound after a crash).
  That means upsert — which collides with §6.

---

## 4. Tenancy, and the scopes it forces

One box per **owner**, not per gym, with a branch id in the schema from the start. The migration cost
is trivial now and awkward later, but a `tenant_id` whose meaning is undecided is worse than no
column at all: it gets defaulted to `"default"` and re-migrated anyway. So the scopes are decided
here.

- **A staff username is unique across the owner's whole estate, not per branch.** A receptionist
  works for the owner and may cover two sites. `branch_id` on `staff` is an *assignment*, not an
  identity scope, which also means the existing unique index on `staff.username` stays correct.
- **The plan catalogue is global per owner.** One price list. It is also the only thing the startup
  seed can honestly do, since it knows nothing about branches (§7).
- **A member is one row with a home branch.** Not one row per branch — duplicate member rows would
  wreck membership numbers and make "how many members do we have" unanswerable. A member training at
  another site is a check-in recorded *at* that branch, which is nearly free because `check_ins`
  already copies name and number rather than joining.
- **The membership-number sequence is global per owner.** A member belongs to the owner; per-branch
  sequences collide the moment someone transfers.

So `branch_id` goes on the location-bound things — `check_ins`, `scheduled_classes`, `equipment` —
and as an attribute on `members` and `staff`. Subscriptions, announcements, therapy and reminders stay
owner-level.

---

## 5. Membership numbers: the most likely day-one outage

`MembershipNumbers.next` computes `max(FIRST, highest_parsed + 1)` over **the entire local members
table**, on every registration and every batch import. Its own KDoc calls it *"a placeholder for a
real numbering policy"*.

Two devices offline for sixty seconds both compute `#88124`. Under the unique index on
`members.membership_number`, this is **not a conflict to resolve — it is a hard insert failure that
aborts the pull transaction**, because device B cannot insert device A's row until B renumbers its
own. `nextRun(count = 8)` makes it eight collisions at once.

**Server-leased blocks.** A device holds about twenty unused numbers in `Settings` and refills when it
runs low. Collision-free by construction, and a wifi drop costs nothing because the lease is already
local. This honours the decision that existing numbers never change: only newly-allocated ones come
from a lease.

The costs, stated: unused leases leave gaps in the sequence, and gaps make an owner think members have
gone missing, so it needs saying out loud in the product. A reinstall loses its lease.

Two interactions worth writing down:

- **A retired number must never be reissued.** `max + 1` over a table that still holds tombstones gets
  that for free — but only if the query does *not* filter out deleted rows, so the obvious cleanup
  silently starts reissuing retired cards. Leases make this moot, which is another argument for them.
- **Legacy sets containing a historical duplicate cannot be imported at all** under the unique index.
  Worth checking against the real gym's data before committing to keeping that index.

The same shape, unsolved, applies to `equipment.asset_tag`: uniqueness there is enforced **in Kotlin
only**, against the local table, with no index at all. It will simply break quietly, and the design
should either add an index or accept advisory duplicates deliberately.

---

## 6. Append-only tables, and why sync fights them

`CheckInDao` exposes `@Insert` and nothing else, deliberately: *"an upsert would let a duplicated id
silently overwrite an earlier entry instead of failing."* But §3 requires pull to be idempotent, and
idempotent means upsert. So sync either deletes that invariant or swallows constraint failures —
ignoring the DAO's only failure signal, which is the exact bug the comment names.

**The resolution is a third path: a sync-ingest DAO surface separate from the app-write surface**,
using `@Insert(onConflict = IGNORE)` and reachable only by the sync writer. The app keeps its strict
insert; the ingest path is explicitly allowed to see a row it already has. This generalises to every
append-only table.

`subscriptions` is the more dangerous case, because it is append-only *by intent but not enforced* —
the DAO exposes only `@Upsert`, so a synced upsert can rewrite `paid_minor_units`, which the entity's
KDoc says must never happen ("a later price change never rewrites history"). Sync turns that
intent/enforcement gap from a latent smell into live audit-trail corruption.

One piece of good news: **`check_ins` two-way sync needs no version column and has no conflicts at
all.** The only correct merge rule is union of ids. It should be one of the first tables done.

---

## 7. What is deliberately not synced

**`reminders`, permanently.** Four reasons, any one sufficient:

- `DefaultReminderSender.attempt()` writes `attempts + 1` *before* the gateway call, because "a crash
  mid-send must not leave the row looking untried". That crash-safety does not hold across devices —
  device B pulls a stale `attempts` and re-sends.
- `ReminderDao.requeue` is a bulk *relative* `UPDATE … attempts = attempts + 1`. Relative updates are
  incompatible with base-version concurrency, and making them per-row loses the documented guarantee
  that "a retry can never leave a QUEUED row still carrying a failure reason".
- `MAX_ATTEMPTS = 4` would silently become a budget shared between two independently-counting devices.
- Decisively: `existingIds`' KDoc warns that a reminder already SENT must not be recreated as QUEUED.
  **Sync is a re-runner using `@Upsert`.** A SENT row syncing back as QUEUED sends a member a
  duplicate renewal notice — precisely what `design/whatsapp-send-system.md` §6 calls the thing that
  makes a gym stop trusting the app.

Cross-device duplicate suppression already has a proper home: `TemplateMessage.idempotencyKey`, which
is the deterministic `renewal:<termId>` id. The WhatsApp design says relay-side suppression "becomes
possible"; this design **upgrades that to a hard requirement of the relay**. Then reminders stay
device-local and nothing is lost.

**`staff` is not synced in v1**, and possibly never in full. Its rows carry raw PBKDF2 salt and hash
BLOBs; syncing them puts password verifiers on a wire and onto a box that also holds the Meta token,
making one target worth attacking twice. The Android backup rules already exclude the database on the
grounds that this data is too sensitive to leave the device, and "the owner controls the box" does not
answer that. Options, in order of preference: push-only for backup and never pulled (the second device
gets its own accounts — annoying, honest, safe); or pull `display_name`, `roles` and `is_enabled` and
never the verifier. If "one login works on both devices" is genuinely required, the document should
say plainly that the verifier is going on the wire, and make TLS and disk encryption on the box stated
requirements rather than assumptions.

Two further staff hazards, both real today:

- **`WouldLockOutDevice` breaks with two devices.** `wouldLoseLastAdministrator` reads a *local*
  snapshot. Two enabled `MANAGE_STAFF` accounts; device A disables one, device B disables the other,
  both pass their local guard, and convergence leaves **zero administrators** — with the guard's own
  justification being "there is no server to recover from". This is the rare case where sync should
  *relax* a local guard, but only once the invariant lives server-side.
- **`observeSession()` will sign people out mid-shift.** It re-derives the session from the local
  `staff` row on every emission. Once `staff` pulls, a synced `is_enabled = 0` fires that from a
  remote, invisible act. `session-expired` — strings built, no caller — finally gets its first
  legitimate use here.

**`subscription_plans` must change before it can sync at all.** `DataModule` registers the seed
`single(createdAtStart = true)`, so **every launch of every device upserts the whole catalogue** with
deterministic ids. Synced, that makes every app start a conflict, and an owner's price change on the
server is overwritten by the next launch — defeating the stated reason plans live in the database at
all. The seed must become **insert-if-absent**, and `SubscriptionDao` has no such method today.

---

## 8. The blob channel

Intake sheets are multi-megabyte photographs of handwritten member PII. They are not rows and need
their own channel, and it carries the headline benefit (§1), so its defects matter.

In order of severity:

1. **Captures live in `context.cacheDir/intake` on Android.** The OS deletes cache under pressure with
   no notification, so an image can vanish between capture and upload — and there is no
   `upload_pending` state and no re-capture path. **Any blob design must first move captures to
   `filesDir`**, which is worth doing on its own merits today: right now an OS cache purge silently
   loses an unreviewed sheet. That move needs its own migration, because existing
   `file://…/cache/intake/…` values in `source_image_uri` become dangling.
2. **`source_image_uri` is a device- and platform-absolute path** fed straight to Coil. Synced to
   another device it is a non-null broken string, so the "no source image" branch does not even fire
   and the review pane shows nothing. It must become **local-only, with a separate syncable
   `blob_id`** resolved per device.
3. **`purgeExcept` derives its keep-set from the local batches table**, so a batch tombstoned on
   device A would make device B purge an image it had never uploaded. It has no production caller
   today — latent, not live — and should be wired *before* it is networked.
4. **Retention.** These are photographs of people who have consented to nothing. Server-side retention
   needs a stated policy mirroring the local deletes in `IntakeIngestion`, and the same
   backup-exclusion reasoning applies at least as hard.

---

## 9. Security and platform

- **The box holds both the sync data and the Meta token.** TLS and disk encryption stop being
  optional. The per-installation credential proposed in `design/whatsapp-send-system.md` §9 should be
  the *same* mechanism, so one device registration serves both.
- **iOS backup is a durability hole and a sync hazard.** The database sits in `NSDocumentDirectory`
  with no exclusion flag, so it rides into iCloud today while Android excludes it. An
  iCloud-restored install would arrive holding a stale *full* database whose ids and versions all look
  legitimate, and push it — considerably worse than the "authenticated nobody" case the auth layer
  already defends against. Setting the flag is right, but it removes iOS's only recovery path, so it
  must land **after** server backup exists. The order matters.
- **iOS has no IO dispatcher.** `Dispatchers.IO` is internal on Kotlin/Native, so `platformIoDispatcher`
  falls back to `Dispatchers.Default` — the same CPU-sized pool Room's queries already use. A
  multi-megabyte blob upload there can starve UI-adjacent reads. Background sync on iOS needs an
  explicit bounded dispatcher, not `dispatchers.io`.
- **Wire DTOs need an explicit `@SerialName` on every field**, for exactly the reason the navigation
  `Config` variants do: R8 renames, and a wire format that shifts with R8's numbering is a
  production-only bug.
- **`:core:network` is deliberately not on `:composeApp`'s classpath**, to keep OkHttp/Darwin/CIO out
  of release artifacts. Sync is the "something real on the other end" that makes that cost payable —
  but it *is* a cost, and it should be budgeted rather than discovered.

### Where the protocol types can live

`:server` may depend on `:core:model` **and nothing else**, machine-enforced by `CheckLayeringTask`,
and CLAUDE.md says not to work around it. That rules out sharing the Room entities, and it also puts
`AppResult`, `Role`, `Permission` and `PasswordHash` out of the server's reach.

- For the durability stage, the server stores **opaque JSON**: `(table, row_id, server_seq, tenant,
  payload)`, never parsed. The layering rule is untouched and no types are shared. The cost is no
  server-side validation and no server-side uniqueness enforcement — acceptable while the server is a
  backup target.
- When server-side *invariants* are needed — the staff lockout guard, number leasing, username
  uniqueness — that stops being enough, and the answer is a `:core:sync-protocol` module plus a
  **deliberate, reviewed amendment** to the layering rule. Not DTOs smuggled into `:core:model`: put
  them there and someone will add `version` and `tenantId` to `Member` itself because it is right
  there, and the domain/storage separation collapses from the domain side.

---

## 10. Staging

Durability first, and **one-way before two-way** — one-way has no conflicts, no rejections, no
tombstone cascade and no membership-number problem.

| Stage | What | Needs a server? |
|---|---|---|
| **0** | Move intake captures `cacheDir` → `filesDir`; wire `purgeExcept`. Independently valuable today. | No |
| **1** | Migration v13: `updated_at`, `sync_outbox`, `sync_tombstones`. Every repository write appends in-transaction. | No |
| **2** | **One-way push to the box — the durability release.** Device id + credential, opaque JSON, manual out-of-band restore into an empty database. Satisfies durability completely. | Yes |
| **3** | Pull, read-only, for desktop review. Exactly one device writes, so still zero write conflicts. | Yes |
| **4** | Two-way, one table at a time: `equipment` → `scheduled_classes` → `check_ins` (union of ids) → `members` (needs the lease and `MemberConflict`'s first caller) → `subscriptions`. | Yes |
| **5** | `staff`, or a documented decision not to. | Yes |

Stage 1's test is the one that matters: one per repository asserting an outbox entry exists for every
write path. That test *is* the proof that no write path was missed.

---

## 11. What this does not solve

- **Real offline-first editing.** The design assumes brief drops. A device that works a full shift
  offline and reconciles later needs machinery this does not have, and pretending otherwise would be
  the same mistake as the offline banner that promised syncing.
- **A member-facing app on this architecture.** A mini-PC behind a gym's consumer router has no static
  IP, is likely behind CGNAT, and cannot run an ACME HTTP-01 challenge. `design/whatsapp-send-system.md`
  §8 already hit this wall with webhooks. Purposes 1–3 are a LAN box; purpose 4 needs a VPS, a tunnel
  or a hosted component in front. **This changes the auth model** — a LAN box is fine with a
  per-installation credential; a public endpoint with member accounts needs real user auth and rate
  limiting, and the box then holds every member's PII behind a port the whole internet can knock on.
  Decide it before buying hardware: "VPS, not mini-PC".
- **Automatic conflict resolution.** `MemberConflict` is a record-level merge UI with two buttons for
  one of fourteen tables. Every other table gets last-write-wins on the server, and that should be
  stated rather than implied.
- **Branch-specific pricing**, cross-branch reporting, and staff working at two branches
  simultaneously. §4 decides the scopes; it does not build the features.

---

## 12. What this needs from you

1. **A box, and a decision about where it lives.** A VPS if a member-facing app is genuinely coming;
   a mini-PC is fine for staff sync and durability alone.
2. **Whether one staff login must work on both devices.** If yes, password verifiers go on the wire
   and that becomes an accepted risk rather than an oversight.
3. **A look at the real gym's existing membership numbers** — specifically whether any duplicate was
   ever issued, because under the current unique index such a set cannot be imported at all.
4. **Whether gaps in the number sequence are acceptable.** Leasing produces them; owners read gaps as
   missing members.
