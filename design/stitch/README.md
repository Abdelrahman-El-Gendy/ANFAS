# Stitch design source

Source of truth for the ANFAS design system. Project:
https://stitch.withgoogle.com/projects/5240614381299664566

`:core:designsystem` is generated **from** these files. When a token changes in Stitch,
re-export here first, then change the Kotlin — never the other way round.

## What to drop here

One folder per screen, named after the screen as it appears in Stitch:

```
design/stitch/
  members-list/
    index.html      <- Stitch's exported markup
    style.css       <- exported styles, if emitted separately
    screen.png      <- screenshot, for layout reference
  member-detail/
  subscriptions/
  ...
```

Anything Stitch gives you is useful. In order of value to me:

1. **HTML/CSS export** — exact hex, font stacks, radii, spacing. This is what the token
   layer is derived from.
2. **PNG screenshot** — layout, hierarchy, and a visual target to diff the built catalog
   against.
3. Screen name — tells me which feature module the screen belongs to.

If Stitch exports the whole project as one bundle, just drop it in as-is; I'll sort it.

## What gets generated from this

`TOKENS.md` in this folder — the extracted inventory of colours, type ramp, radii and
spacing, with each value's source screen. Written by the first pass over the export, and
the thing to read when asking "why is `primary` this hex?".

Do not hand-edit `TOKENS.md`; re-derive it from the export.

---

## How this export was produced (2026-08-21)

`mcp__stitch__download_assets` **does not work** — it returns `Assets downloaded to <dir>` and
writes nothing. Don't rely on it. The working route:

1. `mcp__stitch__list_screens` with `projectId=5240614381299664566` → each screen's
   `htmlCode.downloadUrl` and `screenshot.downloadUrl`. These URLs need no auth.
2. `curl` each `htmlCode.downloadUrl` to `export/<slug>/index.html`.

The HTML download URL is a base64 protobuf in which **only** the `html_<32hex>` file id
varies, so any screen's URL is a constant prefix + that id + a constant suffix:

```
prefix  CgthaWRhX2NvZGVmeBJ7Eh1hcHBfY29tcGFuaW9uX2dlbmVyYXRlZF9maWxlcxpa
suffix  EgsSBxD4oLi0qhQYAZIBIwoKcHJvamVjdF9pZBIVQhM1MjQwNjE0MzgxMjk5NjY0NTY2
url     https://contribution.usercontent.google.com/download?c=<prefix><segment><suffix>&filename=&opi=89354086
```

### What is here and what is not

- ✅ 23 screen HTML exports. Each embeds its own complete `tailwind.config` — **this is where
  the exact token values are**, and it is what `TOKENS.md` is derived from.
- ✅ `design.md`, the project's theme spec, pulled from the project's `designTheme.designMd`.
- ❌ **No PNG screenshots.** `screenshot.downloadUrl` works, but each is a ~200-character
  opaque token that has to be copied by hand from `list_screens`, and the HTML already carries
  the layout truth and renders in a browser. Fetch them if a visual diff target is wanted.
- ❌ No `style.css` — Stitch inlines everything via Tailwind CDN + a `<style>` block.

The 24th screen in the project (`7efbc974a8c145ab86c48e6c0c0f19ec`) is a reference photograph
of a handwritten gym sign-up sheet, used as OCR intake source material. It has no markup.
