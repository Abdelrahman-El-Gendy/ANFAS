---
name: Aurelian Performance
colors:
  surface: '#141311'
  surface-dim: '#141311'
  surface-bright: '#3b3936'
  surface-container-lowest: '#0f0e0c'
  surface-container-low: '#1d1b19'
  surface-container: '#211f1d'
  surface-container-high: '#2b2a27'
  surface-container-highest: '#363432'
  on-surface: '#e6e2de'
  on-surface-variant: '#d6c4b0'
  inverse-surface: '#e6e2de'
  inverse-on-surface: '#32302e'
  outline: '#9e8e7c'
  outline-variant: '#514536'
  surface-tint: '#ffb956'
  primary: '#ffc16c'
  on-primary: '#462b00'
  primary-container: '#e8a33d'
  on-primary-container: '#5f3c00'
  inverse-primary: '#835400'
  secondary: '#b5ccba'
  on-secondary: '#213528'
  secondary-container: '#394e40'
  on-secondary-container: '#a7bead'
  tertiary: '#fdbbc8'
  on-tertiary: '#4e232e'
  tertiary-container: '#dfa0ad'
  on-tertiary-container: '#653541'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffddb5'
  primary-fixed-dim: '#ffb956'
  on-primary-fixed: '#2a1800'
  on-primary-fixed-variant: '#643f00'
  secondary-fixed: '#d1e8d6'
  secondary-fixed-dim: '#b5ccba'
  on-secondary-fixed: '#0c1f14'
  on-secondary-fixed-variant: '#374b3e'
  tertiary-fixed: '#ffd9e0'
  tertiary-fixed-dim: '#f7b5c3'
  on-tertiary-fixed: '#340e1a'
  on-tertiary-fixed-variant: '#683844'
  background: '#141311'
  on-background: '#e6e2de'
  surface-variant: '#363432'
typography:
  headline-lg:
    fontFamily: IBM Plex Sans
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: IBM Plex Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: IBM Plex Sans
    fontSize: 20px
    fontWeight: '500'
    lineHeight: 28px
  body-lg:
    fontFamily: IBM Plex Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: IBM Plex Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-caps:
    fontFamily: IBM Plex Sans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.08em
  data-mono:
    fontFamily: IBM Plex Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 24px
  margin-desktop: 40px
  margin-mobile: 16px
  container-max: 1440px
---

## Brand & Style

The design system is engineered for professional fitness management, balancing high-density data requirements with a calm, focused aesthetic. The brand personality is grounded, premium, and authoritative, moving away from typical "gym-bro" tropes (neon, high-energy gradients, aggressive photography) toward a sophisticated, tool-first environment. 

The design style is **Modern Minimalist with a Tactile Edge**. It prioritizes clarity and structural integrity through:
- **Warmth:** A shift from sterile greys to a warm charcoal and amber-gold palette that feels more "private club" than "warehouse gym."
- **Precision:** Reliance on 1px borders and strict grid alignment to organize complex member data and schedules.
- **Subtlety:** The introduction of a monochromatic cheetah-spot texture used exclusively in structural headers and empty states to provide a whisper of heritage and physical energy without sacrificing legibility.

## Colors

The palette is optimized for long-duration usage by gym staff, utilizing low-eye-strain warm tones.

- **Primary (Amber-Gold):** Reserved for essential actions, active states, and brand-critical indicators. 
- **Secondary (Muted Sage):** Specifically designated for "Recovery," "Therapy," and "Wellness" modules.
- **Tertiary (Dusty Rose):** Specifically designated for "Women’s Programming" and "Group Class" categories.
- **Foundation:** The background uses a warm charcoal (#12110F) to ground the interface, while surfaces use a slightly lighter elevation (#1C1A17) for contrast. All borders should use a 10% opacity version of the text color (#F5F1EA) to create soft definition.

## Typography

This design system uses **IBM Plex Sans** exclusively. Its technical, engineered feel reflects the "management" aspect of the tool.

- **Data Density:** Use the `data-mono` role for tables, financial figures, and member IDs to ensure vertical alignment of digits.
- **Hierarchy:** Headers should be used sparingly to maintain the "calm" interface. Use `label-caps` for secondary metadata and table headers.
- **Color Application:** Headings use the primary off-white (#F5F1EA). Body text can be dropped to 80% opacity for secondary information to create a natural visual hierarchy without adding more colors.

## Layout & Spacing

The layout follows a **Fixed Grid** philosophy on desktop and a **Fluid Grid** on mobile.

- **Desktop (12 Columns):** 24px gutters. Content is housed in "Surfaces" (#1C1A17). To maintain the "calm" feel, avoid packing elements tightly; use generous internal padding (minimum 24px) within card containers.
- **Staff View:** For data-heavy tables (Member Lists, Attendance), reduce vertical row padding to 12px but maintain wide horizontal margins to prevent visual clutter.
- **Breakpoints:** 
  - Mobile: < 600px (4 columns, 16px margins)
  - Tablet: 600px - 1024px (8 columns, 24px margins)
  - Desktop: > 1024px (12 columns, 40px margins)

## Elevation & Depth

This design system eschews heavy shadows in favor of **Tonal Layers and Low-Contrast Outlines**.

- **Layer 0 (Background):** #12110F.
- **Layer 1 (Cards/Surfaces):** #1C1A17. Elements on this layer must have a 1px solid border using #F5F1EA at 10% opacity.
- **Layer 2 (Modals/Popovers):** #252320. These are the only elements allowed to have a shadow: use a very soft, diffused shadow (0px 8px 24px) with 40% black opacity.
- **Texture:** The cheetah-spot motif should be applied as a `mask-image` or a `multiply` blend mode at 5% opacity. It appears only in the top 120px of page headers or centered within empty-state containers. It must never overlap text or interactive elements.

## Shapes

The shape language is consistent and approachable.
- **Base Radius:** All cards, input fields, and containers use a **12px (0.75rem)** corner radius.
- **Interactive Elements:** Buttons and tags follow the same 12px radius unless they are "status chips," which may be pill-shaped (3) to distinguish them from actionable buttons.
- **Selection:** Checkboxes and radio buttons should maintain a 4px radius rather than being fully circular to match the technical aesthetic of IBM Plex.

## Components

- **Buttons:** 
  - *Primary:* Solid #E8A33D with #12110F text. No gradients.
  - *Secondary:* 1px border of #E8A33D with #E8A33D text.
- **Input Fields:** Background #12110F, 1px border (#F5F1EA @ 10%), 12px radius. Focused state: 1px border #E8A33D.
- **Data Tables:** Headers in `label-caps`. Rows separated by 1px solid lines (#F5F1EA @ 5%). No alternating row colors; use hover states (#F5F1EA @ 2%) for navigation.
- **Status Chips:** 
  - *Recovery:* Sage (#7A9080) background at 15% opacity, solid Sage text.
  - *Class Type:* Rose (#B87D8A) background at 15% opacity, solid Rose text.
- **Cards:** No shadows. Define boundaries using the 1px border (#F5F1EA @ 10%). Use the cheetah texture subtly in the "Header" section of the card if it’s a featured dashboard widget.
