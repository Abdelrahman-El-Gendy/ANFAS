# TOKENS.md — extracted design tokens

> **Generated. Do not hand-edit.** Re-derive from `design/stitch/export/` after every re-export.

- **Source:** Stitch project `5240614381299664566` — *FAHD Professional Management Suite*
- **Theme:** `Aurelian Performance` (dark only, `colorMode: DARK`, `colorVariant: FIDELITY`)
- **Screens analysed:** 23 HTML exports (a 24th screen is a reference photograph, no markup)
- **Method:** every screen embeds its own `tailwind.config`; values below are agreed across screens, with the count shown.

## Colour — Material 3 roles

The generated M3 palette. Unanimous across all screens unless noted.

| token | hex | screens |
|---|---|---|
| `background` | `#141311` | 22/23 ⚠️ |
| `error` | `#ffb4ab` | 22/23 ⚠️ |
| `error-container` | `#93000a` | 23/23 |
| `inverse-on-surface` | `#32302e` | 23/23 |
| `inverse-primary` | `#835400` | 23/23 |
| `inverse-surface` | `#e6e2de` | 23/23 |
| `on-background` | `#e6e2de` | 23/23 |
| `on-error` | `#690005` | 23/23 |
| `on-error-container` | `#ffdad6` | 23/23 |
| `on-primary` | `#462b00` | 23/23 |
| `on-primary-container` | `#5f3c00` | 23/23 |
| `on-primary-fixed` | `#2a1800` | 23/23 |
| `on-primary-fixed-variant` | `#643f00` | 23/23 |
| `on-secondary` | `#213528` | 23/23 |
| `on-secondary-container` | `#a7bead` | 23/23 |
| `on-secondary-fixed` | `#0c1f14` | 23/23 |
| `on-secondary-fixed-variant` | `#374b3e` | 23/23 |
| `on-surface` | `#e6e2de` | 23/23 |
| `on-surface-variant` | `#d6c4b0` | 23/23 |
| `on-tertiary` | `#4e232e` | 23/23 |
| `on-tertiary-container` | `#653541` | 23/23 |
| `on-tertiary-fixed` | `#340e1a` | 23/23 |
| `on-tertiary-fixed-variant` | `#683844` | 23/23 |
| `outline` | `#9e8e7c` | 23/23 |
| `outline-variant` | `#514536` | 23/23 |
| `primary` | `#ffc16c` | 23/23 |
| `primary-container` | `#e8a33d` | 23/23 |
| `primary-fixed` | `#ffddb5` | 23/23 |
| `primary-fixed-dim` | `#ffb956` | 23/23 |
| `secondary` | `#b5ccba` | 23/23 |
| `secondary-container` | `#394e40` | 23/23 |
| `secondary-fixed` | `#d1e8d6` | 23/23 |
| `secondary-fixed-dim` | `#b5ccba` | 23/23 |
| `surface` | `#141311` | 23/23 |
| `surface-bright` | `#3b3936` | 23/23 |
| `surface-container` | `#211f1d` | 23/23 |
| `surface-container-high` | `#2b2a27` | 23/23 |
| `surface-container-highest` | `#363432` | 23/23 |
| `surface-container-low` | `#1d1b19` | 23/23 |
| `surface-container-lowest` | `#0f0e0c` | 23/23 |
| `surface-dim` | `#141311` | 23/23 |
| `surface-tint` | `#ffb956` | 23/23 |
| `surface-variant` | `#363432` | 23/23 |
| `tertiary` | `#fdbbc8` | 23/23 |
| `tertiary-container` | `#dfa0ad` | 23/23 |
| `tertiary-fixed` | `#ffd9e0` | 23/23 |
| `tertiary-fixed-dim` | `#f7b5c3` | 23/23 |

## Colour — brand palette used OUTSIDE the token set

These come from the prose half of `design.md` and appear as **raw hex in markup**, not as
Tailwind tokens. They are real and must be modelled, or those screens cannot be built.

| hex | role (from design.md prose) | raw uses | screens |
|---|---|---|---|
| `#f5f1ea` | Off-white text/border base — borders at 10%, hover at 2%, table rules at 5% | 22 | 7 |
| `#b87d8a` | Rose — group-class / women’s programming chips | 13 | 4 |
| `#7a9080` | Sage — recovery / therapy / wellness chips | 11 | 4 |
| `#1c1a17` | Charcoal — card surface in the prose palette | 5 | 2 |

## Typography

Single family: **IBM Plex Sans** for every role (`data-mono` included — it is *not* a mono face).

| role | size | line-height | weight | letter-spacing |
|---|---|---|---|---|
| `headline-lg` | 32px | 40px | 600 | -0.02em |
| `headline-md` | 24px | 32px | 600 | -0.01em |
| `headline-sm` | 20px | 28px | 500 | — |
| `body-lg` | 16px | 24px | 400 | — |
| `body-md` | 14px | 20px | 400 | — |
| `label-caps` | 11px | 16px | 600 | 0.08em |
| `data-mono` | 14px | 20px | 400 | — |

## Radius

| token | value | screens |
|---|---|---|
| `DEFAULT` | 0.25rem | 23/23 |
| `lg` | 0.5rem | 23/23 |
| `xl` | 0.75rem | 23/23 |
| `full` | 9999px | 23/23 |
| `form` | 12px | 1/23 |

## Spacing

| token | value | screens |
|---|---|---|
| `container-max` | 1440px | 21/23 |
| `gutter` | 24px | 21/23 |
| `margin-desktop` | 40px | 21/23 |
| `margin-mobile` | 16px | 21/23 |
| `unit` | 4px | 21/23 |

## Elevation

No `boxShadow` token exists in any of the 23 exports. The prose in `design.md` specifies the
model instead, and it is deliberately shadow-free:

| layer | surface | treatment |
|---|---|---|
| 0 — background | `#141311` | none |
| 1 — cards | `surface-container` `#211f1d` | 1px border, off-white @ 10%. **No shadow.** |
| 2 — modals/popovers | `surface-container-high` `#2b2a27` | the only shadow: `0 8px 24px rgba(0,0,0,0.40)` |

Table rules are off-white @ 5%; row hover is off-white @ 2%.

## Conflicts and deviations

Real disagreements found. Each needs a decision before the token layer is frozen.

| # | issue | detail | resolution taken |
|---|---|---|---|
| 1 | `background` | `#141311` on 22 screens, `#12110F` on `staff-login`. The **prose** also says `#12110F`. | Use `#141311` — 22/23 and matches `surface`/`surface-dim`. `staff-login` is the outlier. |
| 2 | `error` | `#ffb4ab` on 22 screens; `failed-reminder-detail` uses raw Tailwind red `#ef4444`. | Use `#ffb4ab`. `#ef4444` is an escape from the palette, not a token. |
| 3 | radius scale shifted | `design.md` front-matter: `sm .25 / DEFAULT .5 / md .75 / lg 1 / xl 1.5rem`. Every export: `DEFAULT .25 / lg .5 / xl .75 / full`. | Trust the **exports**. The prose "base radius 12px" = `0.75rem` = exported `xl`. |
| 4 | prose palette ≠ token palette | Prose describes `#12110F` bg, `#1C1A17` card, `#F5F1EA` text, `#252320` modal. The token block and all screens use the M3 set (`#141311`, `#211f1d`, `#e6e2de`, `#2b2a27`). | Token set wins as the base; the four prose colours survive only as the brand accents above. |
| 5 | `data-mono` is not mono | Named for digit alignment but resolves to IBM Plex Sans in all 23 exports. | Ship as IBM Plex Sans with tabular figures (`fontFeatureSettings("tnum")`) to get the intent. |
| 6 | dark only | `colorMode: DARK`. No light palette exists anywhere in the project. | Single dark scheme. Do not invent a light one. |

## Screen inventory → module mapping

23 screens with markup. `M` = mobile (780w), `D` = desktop (2560w).

| screen | | target module |
|---|---|---|
| `staff-login` | M | **gap** — no auth feature module exists; `:core:auth` holds session/RBAC only |
| `staff-dashboard` | M | **gap** — no dashboard module |
| `reception-dashboard` | D | **gap** — no dashboard module |
| `live-checkin-log` | M | **gap** — check-in has no module |
| `member-directory` | M | `:feature:members` |
| `member-profile` | M | `:feature:members` |
| `members-list-empty` | D | `:feature:members` |
| `search-no-results` | D | `:feature:members` |
| `renewal-sheet` | M | `:feature:subscriptions` |
| `whatsapp-reminder-queue` | D | `:feature:subscriptions` |
| `reminder-queue-empty` | D | `:feature:subscriptions` |
| `failed-reminder-detail` | D | `:feature:subscriptions` |
| `ocr-intake-review` | D | `:feature:intake-ocr` |
| `ocr-intake-empty` | D | `:feature:intake-ocr` |
| `therapy-case-file` | D | `:feature:therapy` |
| `class-schedule` | M | `:feature:classes` |
| `weekly-class-schedule` | D | `:feature:classes` |
| `create-announcement` | D | `:feature:announcements` |
| `equipment-detail` | D | `:feature:equipment` |
| `sync-conflict` | D | cross-cutting state → `:core:designsystem` |
| `offline-banner` | D | cross-cutting state → `:core:designsystem` |
| `session-expired` | D | cross-cutting state → `:core:designsystem` |
| `permission-denied` | D | cross-cutting state → `:core:designsystem` |

**Coverage note:** every existing `:feature:*` module has at least one screen. Four screens
(login, two dashboards, check-in log) have **no** module in the graph — see CLAUDE.md's module
list. Four more are cross-cutting states that belong in the design system, not a feature.
