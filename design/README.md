# ANFAS — Design Brief for Claude Design

> **Audience:** Claude Design (or any designer/agent producing UI). **Goal:** a complete,
> user-friendly, professional experience for ANFAS across phone, tablet and desktop, in English
> and Arabic (RTL). Read this whole file first; it states what the product is, who uses it, what
> every screen must do, what is *deliberately not built*, and the rules that keep the design
> honest. Engineering rules live in [`/CLAUDE.md`](../CLAUDE.md); the extracted token tables are in
> [`stitch/TOKENS.md`](stitch/TOKENS.md) and the visual-system prose in
> [`stitch/design.md`](stitch/design.md). Where this brief and those disagree on a colour or
> radius, **TOKENS.md wins** (it was reconciled against the real exports).

---

## 1. What ANFAS is

ANFAS is a **gym and recovery-clinic management app for the staff who run the floor** — not a
member-facing app. One Kotlin Multiplatform codebase ships to **Android, iOS (iPhone + iPad),
and Desktop (macOS/Windows/Linux)**. It is **offline-first**: all data lives in a local database
on the device; there is no auth server and no sync yet.

It replaces paper sign-up sheets, WhatsApp chasing, a whiteboard timetable and a notebook of
therapy notes with one calm, fast tool.

**Product personality:** grounded, premium, authoritative, *tool-first*. A "private club", not a
"warehouse gym". No neon, no aggressive gradients, no gym-bro photography. Calm density: lots of
data, never noisy.

## 2. Users and roles

Staff sign in with a username + password (accounts are created on-device by the Owner). Roles
decide what each person sees — **hide what a role cannot use, never show a disabled control**
(exception: explain *why* when the missing piece is configuration, e.g. WhatsApp not connected).

| Role | Day-to-day | Can do |
|---|---|---|
| **Owner** | Runs the business | Everything, incl. creating/disabling staff accounts |
| **Admin** | Runs the gym | Everything except staff management |
| **Receptionist** | The front desk | Members (view/edit), renewals & payments, reminders (view/retry/send), intake scan + import, check-in, classes, announcements, equipment |
| **Coach** | On the floor | View members, check people in, scan intake sheets. Sees timetable + equipment status (read-only) |
| **Therapist** | Recovery clinic | View members, check-in, **therapy case files** (the only role besides Owner/Admin) |

Primary persona: **the receptionist at the desk at 6 pm rush** — one hand on a phone/tablet,
a queue of people, needs the answer in under two seconds. Secondary: the owner reviewing on a
desktop at the end of the day; the therapist writing clinical notes.

## 3. Design principles (apply to every screen)

1. **Answer first.** Every screen opens on the thing the person came for (who's expiring, what's
   on now, is this member allowed in) — chrome second.
2. **Honest data only.** Never show a figure the app cannot compute. If something isn't backed by
   real data, omit it — don't fake a chart, delta or "today's check-ins" number.
3. **Status is visible, never colour-only.** Chip = colour + label (+ icon). Expired/refused is
   always spelled out.
4. **Reachable one-handed on a phone.** Primary actions in the lower half; touch targets ≥ 48dp.
5. **Every list has four states designed:** loading, empty (with an invitation), no-results, error.
6. **Destructive and money actions confirm; everything else is undoable or instant.**
7. **Calm density.** Tables use 12px row padding with wide horizontal margins; no zebra rows.
8. **Same words, same place.** One name per concept across all screens and both languages.

## 4. Visual system — "Aurelian Performance" (dark only)

**Dark theme only.** There is no light palette; do not invent one.

| Role | Hex | Use |
|---|---|---|
| `background` / `surface` | `#141311` | Page (layer 0) |
| `surface-container` | `#211f1d` | Cards (layer 1) — 1px border, off-white `#f5f1ea` @ 10%, **no shadow** |
| `surface-container-high` | `#2b2a27` | Modals/popovers (layer 2) — the *only* shadow: `0 8px 24px rgba(0,0,0,.4)` |
| `on-surface` | `#e6e2de` | Text; secondary text at ~80% |
| `primary` | `#ffc16c` | Active states, key actions |
| `primary-container` | `#e8a33d` | **Primary button fill** (text `#141311`), amber-gold |
| `secondary` (Sage) | `#b5ccba` / chip `#7a9080` | **Recovery / Therapy / Wellness only** |
| `tertiary` (Rose) | `#fdbbc8` / chip `#b87d8a` | **Group classes / women's programming only** |
| `error` | `#ffb4ab` | Errors, refused, failed |
| Borders | `#f5f1ea` @ 10% (chrome edges) · @ 5% (table rules) · @ 2% (row hover) | |

- **Type:** IBM Plex Sans for everything. Roles: headline-lg 32/40 · headline-md 24/32 ·
  headline-sm 20/28 · body-lg 16/24 · body-md 14/20 · **label-caps** 11/16 (+0.08em, table headers
  and metadata) · **data-mono** 14/20 with tabular figures (IDs, phones, money, dates in tables).
  Arabic uses its own script face at matching sizes.
- **Radius:** 12px for cards, inputs, buttons; pill for status chips; 4px for checkboxes.
- **Spacing:** 4px unit · 16px mobile margin · 24px gutter · 40px desktop margin · 1440px max
  content width (centre on wide screens).
- **Texture:** a monochrome cheetah-spot motif at ~5% opacity, *only* in the top 120px of page
  headers and centred in empty-state containers. Never behind text or controls.
- **Buttons:** primary = solid amber, no gradient; secondary = 1px amber border + amber text;
  muted text action for tertiary. **Inputs:** `#141311` fill, 10% border, amber 1px focus.
- **Status chips (tones):** Positive (active/granted), Critical (expired/refused/failed),
  Warning (amber — "needs service", expiring), Neutral, Recovery (sage), Class type (rose).
- **Breakpoints:** phone < 600 · tablet 600–1024 · desktop > 1024 (rail layout from 1024).
- **Avatars are initials** in a circle — there are no photos anywhere.
- **Motion:** M3 duration/easing tokens (short 50–200ms for state, medium 250–400ms for
  transitions); interruptible; honour reduced-motion.

## 5. Navigation model

| Placement | Where | Destinations |
|---|---|---|
| **Primary** (bottom bar *and* rail — max 4) | Everywhere | **Dashboard · Members · Schedule · Check-in** |
| **Wide-only** (rail; on phone reached from a parent screen) | | Reminders (from the dashboard's *needs renewal* tile), Intake/Scan (from the Members directory) |
| **Desktop-only** (rail only; no phone screen exists) | | Announcements, Equipment |
| **Account** | Rail footer / compact overflow | Staff management, language (EN ⇄ AR), sign out |

- Phone: 4-item bottom bar, icon + label. Tablet/desktop: 256dp left rail (8 destinations).
- Pushed screens (member profile, renewal, therapy case, reminder detail) use a **detail top bar
  with back + title** — don't repeat the title in a screen header. Desktop has no back gesture, so
  the bar is mandatory there.
- Bar highlight maps: Intake → Members, Staff → nothing.
- Language switch is always one tap away and flips the entire layout (RTL mirroring, directional
  icons auto-mirror; **never** use "→" in translatable copy — write "from X to Y").

## 6. Screens to design

Mobile = M, Desktop/tablet = D (both unless stated). Each lists the job, the content, and the
states that **must** be designed.

### 6.1 Staff sign-in  `M+D`
Brand mark + username + password + Sign in. **First-run** variant (no accounts exist): "Create
the owner account" form. Errors are generic ("incorrect username or password" — never say which).
Disabled-account state. No "Forgot password", no "Remember me". On short viewports (landscape
phone + keyboard) drop the heading so the fields stay visible.

### 6.2 Reception dashboard  `D (adapts to M)`
Real tiles only: **members needing renewal** (expired sorted first, then expiring soon — links
to Reminders), **failed reminders** (shown only if > 0), active members, expiring this month,
and the renewal queue list with member, plan, days left/overdue. One failing source ⇒ the whole
screen shows a retry error (a missing tile reads as "zero").
*Not designed:* today's check-ins count, % deltas, capacity, cleanliness, equipment issues.

### 6.3 Member directory  `M+D`
Search (name/phone/number) + status filter + list/table. Row: initials avatar, name, membership
no., phone (LTR-isolated), plan chip, status chip, term end, last check-in. Phone = cards;
≥ `AnfasTableMinWidth` = table with `label-caps` headers. Primary action "Add member"; secondary
"Scan sheets" (opens Intake). **States:** empty directory (invitation + cheetah texture),
search no-results, loading, error.

### 6.4 Member profile  `M+D`
Header (avatar, name, no., status chip, contact), **subscription term progress bar** (Critical
tone when expired/expiring), plan + dates, check-in history snippet, WhatsApp opt-in toggle +
preferred language, actions: **Renew**, **Edit**, **Therapy** (hidden without permission),
Delete (confirm). "Member not found" state for a deleted id.

### 6.5 Renewal sheet  `M (dialog on D)`
Pick plan (choice cards with price + duration), start date via **date picker** (never typed
dates), payment method, total, Confirm. Shows the new end date before confirming.

### 6.6 Live check-in log  `M+D`
Search/scan a member → instant **Granted / Refused** result card (refused states the reason:
expired, frozen, not found; a not-yet-started term *grants*). Below: today's log (time, member,
outcome chip). The outcome is decided by the system, never chosen by staff.
*Not designed:* capacity/"in the gym now" (no check-out exists).

### 6.7 Class schedule  `M` (day list) and `D` (weekly grid)
A class is a **recurring weekly slot** (no dates/weeks). Phone: day tabs + chronological list
("what's on today"). Wide: 7-day grid with hour axis; overlapping classes sit in side-by-side
lanes so none is hidden. Class block: time, name, instructor (or "Unassigned" — a real state),
room, type tint (rose for group classes). Add/edit dialog (day, start/end, room, instructor,
capacity) for `MANAGE_CLASSES`; **room clash = warning banner, still saved**. Everyone can view.
Switch day-list ⇄ grid by the grid's *own* width, not window width.

### 6.8 WhatsApp reminder queue  `D (cards on M)`
Tabs by status (Queued · Sent · Failed · All), template filter, bulk select. Row: member,
template, scheduled time, attempts, status chip, failure reason (4dp error stripe on failed).
Actions: **Build queue** (itemised result: "N queued · 1 not opted in · 1 without phone · 1
already reminded"), **Run queue** (hidden with an explanation *"WhatsApp isn't connected yet"*
until a gateway exists), Retry. **Failed-reminder detail:** reason in plain language, attempts,
what to do next. **Empty state** names the *Build queue* button. "Sent" ≠ "Delivered".

### 6.9 OCR intake — scan & review  `D (stacked on M)`
Photograph a handwritten sign-up sheet → review. Split view: **source photo** (left) with
overlay boxes, **extracted rows** (right) with a per-cell **confidence stripe** and inline edit
(dates here are inline text, because they transcribe handwriting). Row validation errors
(duplicate phone, bad date). Actions: Import (needs `IMPORT_INTAKE`), Discard. Below the
breakpoint the source pane stacks above rows. **States:** empty ("scan your first sheet"), camera
permission denied (iOS: explain the OS toggle), source image missing.

### 6.10 Therapy case file  `D (stacked on M)`
Reached only from a member profile. Case header (condition, therapist, opened date, status),
**pain-score sparkline** from real recorded scores with copy "from 7 to 4", session log (date,
notes, pain score), Log session, Close case. One open case per member; closed cases are history.
Sage accent. Banner: "Therapist and owner access only."
*Not designed:* next appointment, files/attachments, range-of-motion figures.

### 6.11 Announcements  `D only`
Archive list + composer **dialog**: title, body (plain text), audience (All members · Active
only · Expiring this month — with a live recipient count), Save draft / Publish. A published
announcement can only be edited ("Save changes") — no unpublish, no re-publish. Note on screen
that member delivery isn't connected yet.

### 6.12 Equipment  `D only`
Inventory table (name, asset tag, area, status chip: Operational · **Needs service** (amber) ·
Out of order (critical), last service). Row opens a **detail dialog**: info, maintenance log
(issue report vs. service record by technician), Log maintenance (note, technician optional,
resulting status). Duplicate asset tags are refused with a clear message. Everyone can view; only
`MANAGE_EQUIPMENT` edits.

### 6.13 Staff management  `Owner/Admin account area`
List of staff (avatar initials, role chips, enabled state); add, change roles, reset password,
disable (ends the live session). A person may hold multiple roles (e.g. Coach + Therapist).

### 6.14 Cross-cutting states (components, not pages)
- **Permission denied** — full-screen, plain language, "ask the gym owner for access".
- **Session expired** — empty-state pattern + "Sign in again".
- **Offline banner**, **Sync conflict** (member field-by-field "keep mine / keep theirs") — design
  them, but they have **no caller yet** (no sync); don't show a promise the app can't keep.
- **Loading** skeletons, **error with retry**, **empty with invitation**, **no results**.

### 6.15 Implementation status (as of 2026-10-03, DB schema v13)

Design everything marked **Live** as fully working. Design **Built, not connected** items with
their honest "not available yet" state. Never design **Not built** items.

| Area | Status | Notes |
|---|---|---|
| Staff sign-in, first-run setup | **Live** | Local accounts, PBKDF2; disabled/demoted accounts take effect immediately |
| Staff management | **Live** | Owner only: create, roles, reset, disable |
| Roles & permissions | **Live** | Owner, Admin, Receptionist, Coach, Therapist; route guard + permission-denied screen |
| Reception dashboard | **Live** | Renewal queue, failed reminders, active/expiring counts |
| Members (directory, profile, add/edit/delete) | **Live** | Search, filters, empty/no-results states |
| Subscriptions (plans, renewal, payments) | **Live** | Date picker, term progress |
| Check-in | **Live** | System-decided granted/refused, today's log |
| Classes | **Live** | Weekly slots, day list + week grid, room-clash warning |
| OCR intake | **Live** | Capture, recognition, review with confidence, import |
| Therapy case file | **Live** | Pain sparkline, sessions, close case |
| Announcements (desktop only) | **Live** | Composer, audience count, publish |
| Equipment (desktop only) | **Live** | Inventory, maintenance log, status |
| English / Arabic + RTL | **Live** | Language toggle on every platform |
| Reminder queue (build, retry, itemised results) | **Live** | Queue screen table/cards |
| Run queue / WhatsApp sending | **Built, not connected** | No gateway; action hidden with explanation |
| Announcement delivery to members | **Built, not connected** | Count is real; nobody is notified |
| Sync (outbox + tombstones) | **Built, not connected** | Records changes only; no server |
| Sync-conflict, offline banner, session-expired | **Built, not connected** | Components exist, no caller |
| Staff dashboard (capacity, cleanliness, equipment) | **Not built** | No data backs it |
| "Today's check-ins" count, % deltas | **Not built** | No history kept |
| Gym capacity / who's inside | **Not built** | No check-out exists |
| Therapy roster, appointments, files, range-of-motion | **Not built** | |
| Announcement rich text, images, scheduling, unpublish; class/therapy audiences | **Not built** | |
| Payment-due & marketing reminders | **Not built** | |
| Light theme · member-facing app | **Not built** | |
| Forgot password · Remember me | **Not built** | |
| Cancel a single class date / holidays | **Not built** | |

## 7. Content & copy

- Voice: short, plain, respectful, second person. "Renew membership", not "Initiate renewal".
- Every string exists in **English and Arabic**; budget **+30%** width for Arabic and test truncation.
- Numerals, phones, IDs and currency codes are Latin and LTR-isolated inside Arabic text; localised
  dates/months are **not** forced LTR.
- Currency: EGP (`ج.م` in Arabic). Dates are picked, never typed.
- Empty-state copy always names the next action ("Build queue", "Scan a sheet", "Add member").
- Error copy says what happened and what to do; never blame the user; never expose codes.

## 8. Accessibility (non-negotiable)

WCAG AA contrast (4.5:1 body, 3:1 large/UI) on the dark palette · touch targets ≥ 48dp · all
icon-only controls labelled · composite rows read as one TalkBack/VoiceOver item · section titles
are headings · logical focus/tab order on desktop with visible amber focus rings · full keyboard
operation on desktop · dynamic type / font-scale tolerant layouts · reduced-motion respected ·
status never conveyed by colour alone · RTL mirrors layout and directional icons.

## 9. What was deliberately **not** built (do not design these)

Staff dashboard with capacity/cleanliness/equipment figures · today's check-in counts and
percentage deltas · gym capacity ("who's inside") · therapy roster, appointments, attachments,
range-of-motion · announcement rich text/images/scheduling/unpublish · light theme · member-facing
app · "Forgot password" / "Remember me" · per-date class cancellation · send-test-message.
If a design needs one of these to feel complete, propose an honest alternative instead.

## 10. Deliverables expected from Claude Design

1. **Design system sheet:** tokens above as variables, type scale (Latin + Arabic), components
   (buttons, fields, date field, chips, banners, table, cards, dialog, nav bar/rail, avatar,
   progress bar, sparkline, empty/error/loading states) with all interaction states.
2. **Every screen in §6** at phone (≈390w), tablet (≈820w) and desktop (≈1440w), **EN (LTR) and
   AR (RTL)**.
3. **State matrix** per list screen: loading · empty · no-results · error · populated · long-text.
4. **Key flows as clickable prototypes:** sign-in → dashboard → check-in a member · renew an
   expired member · scan sheet → review → import · build reminder queue · log a therapy session ·
   add a class with a room clash · log equipment maintenance.
5. **Handoff notes** per screen (tokens not pixels, edge cases, a11y) so engineering can map
   directly to Compose Multiplatform + Material 3 (`:core:designsystem`).

## 11. Reference

- Source screens: Stitch project `5240614381299664566` (23 exports in
  [`stitch/export/`](stitch/export)); screen→module map in [`stitch/TOKENS.md`](stitch/TOKENS.md).
- Stack for fidelity: Kotlin Multiplatform, Compose Multiplatform 1.11 + Material 3, Decompose
  navigation, Room KMP (local DB), IBM Plex Sans.
- Planned (not designed yet): sync layer ([`sync-layer.md`](sync-layer.md)) and live WhatsApp
  sending ([`whatsapp-send-system.md`](whatsapp-send-system.md)).
