# UI Design Principles: Skeuomorphic Cassette Futurism

**Version 1.1 — Internal Design Reference**

---

## 1. Vision & Aesthetic Philosophy

This UI lives at the intersection of **analog warmth** and **retro-futurist optimism** — a world where the future was imagined through the lens of 1970s–80s hardware. Think Alien (1979) ship terminals, Blade Runner data readouts, NASA mission control panels, and consumer electronics from the Walkman era. Every element should feel like it was **manufactured**, not rendered.

The guiding question for every design decision: *"Could this exist as a physical object?"*

---

## 2. Color Palette

The palette is drawn from **Faber Birren's 1944 industrial color safety code** — the system that painted every mid-century control room, reactor facility, and factory floor. Colors are **functional, not decorative**: each hue carries a specific semantic role and must not be repurposed. Tones are deliberately soft to reduce visual fatigue and avoid distraction during sustained attention tasks.

| Role | Name | Hex | Usage |
|---|---|---|---|
| Background | **Reactor Black** | `#0D0F0D` | Primary surface — dark with a faint green cast |
| Panel | **Machinery Gray** | `#1E221E` | Raised hardware panels, equipment housings |
| Surface / Bezel | **Industrial Gray** | `#4A524A` | Bezels, knob surrounds, structural trim |
| Inactive Surface | **Birren Beige** | `#D4C5A9` | Interiors without natural light; inactive/disabled surfaces |
| Primary Accent | **Seafoam** | `#7EC8A0` | Active states, selected indicators, primary glow — the signature color |
| Secondary Accent | **Solar Yellow** | `#C8A84B` | Caution states, in-progress indicators, secondary highlights |
| Danger | **Fire Red** | `#C4392F` | Warnings, errors, peak indicators — **reserved exclusively for danger** |
| Text Primary | **Phosphor White** | `#E8F0E8` | Body text on dark surfaces — cool white with a green cast |
| Text Secondary | **Faded Green** | `#7A8E7A` | Labels, metadata, inactive text |
| Dado Green | **Medium Green** | `#5B7A5B` | Lower panel borders, dado rails, structural dividers |

**Rules:**
- Never use pure `#FFFFFF` or `#000000` — everything carries a slight green or gray cast
- **Panels, bezels, and housings are always neutral** — Machinery Gray `#1E221E` / Industrial Gray `#4A524A` only. Never tint these surfaces Seafoam/green; reserve green for screens, walls, and active states.
- **Fire Red is reserved for danger only** — never use it decoratively (Birren's code: fire protection, emergency stops, errors)
- **Solar Yellow is reserved for caution** — in-progress states, non-critical warnings
- **Seafoam is the primary accent** — glow effects use Seafoam only; never amber, blue, or purple
- Limit active accent colors to 2 per screen
- Inactive/disabled surfaces use Birren Beige, not a darkened version of the active color

---

## 3. Typography

| Role | Typeface | Style | Notes |
|---|---|---|---|
| Display / Headers | **Share Tech Mono** or **VT323** | All-caps, tracked | Mimics dot-matrix or 7-segment displays |
| Body / Labels | **Courier Prime** or **IBM Plex Mono** | Regular | Typewriter feel, high legibility |
| Data Readouts | **Digital-7** or **DSEG7** | Numeric only | Authentic 7-segment LCD aesthetic |
| Stencil / Markings | **Stardos Stencil** | Uppercase | Panel labels, hardware markings |

**Rules:**
- Monospace is the default — proportional fonts are the exception, not the rule
- Letter-spacing on headers: `+0.1em` to `+0.2em`
- No font weights above **Regular/400** — bold is achieved through size and color, not weight
- Text on dark panels should have a subtle `text-shadow` glow in the accent color (`0 0 6px #7EC8A0`)

---

## 4. Texture & Materials

Every surface should read as a **physical material**. Use layered CSS or SVG textures.

| Surface | Material | Treatment |
|---|---|---|
| Primary panels | Brushed aluminum / matte ABS plastic | Subtle horizontal grain, slight sheen |
| Recessed areas | Rubberized matte | Flat, no reflection, slight noise texture |
| Buttons | Molded plastic or metal | Convex bevel, specular highlight at top-left |
| Tape elements | Magnetic oxide tape | Brown, slightly reflective, thin |
| Screen areas | CRT phosphor | Scanline overlay (`repeating-linear-gradient`), slight vignette, subtle barrel distortion |
| Knobs/dials | Machined metal | Radial gradient, knurled edge texture |

**Texture implementation:**
- Use `noise.png` or SVG `feTurbulence` filters for surface grain
- CRT screens: scanlines at 2px intervals, 8–12% opacity
- All panels have a **1px inner highlight** at top/left edges and a **1px inner shadow** at bottom/right — this is non-negotiable for the physical feel

---

## 5. UI Components

### Buttons
- Convex shape — raised from the panel surface
- Resting state: top-left specular highlight, bottom-right shadow
- Pressed state: invert shadows (concave), slight color shift, no animation bounce — **snap**, not spring
- Optional: embossed label text

### Sliders & Faders
- Vertical faders preferred over horizontal (mixing board aesthetic)
- Track is a recessed channel (inset shadow)
- Thumb is a chunky, tactile grip — knurled texture or ribbed rubber
- Travel marks/tick marks are mandatory

### Knobs & Dials
- Circular, with a visible **indicator line** (not a dot)
- Surrounding ring shows tick marks and range labels in stencil font
- Rotation feedback: the indicator line moves; no fill animations

### VU Meters & Level Indicators
- Segmented bar displays (not smooth gradients)
- **Seafoam → Solar Yellow → Fire Red** progression (mirrors Birren's safety color hierarchy)
- Segments have visible gaps between them
- Peak hold indicator: a single segment that lingers 1–2s before dropping

### Tape Reels (decorative/functional)
- Can be used as loading indicators or progress elements
- Two reels: supply (left, decreasing) and take-up (right, increasing)
- Rotation speed proportional to playback/progress speed

### Displays & Screens
- All screen content sits inside a **bezel** — never edge-to-edge
- Bezel has rounded corners, physical depth (shadow), and a subtle reflection
- Screen content has scanlines, slight **seafoam green** tint, and a vignette

### Toggle Switches
- Physical rocker or flip switches, not iOS-style pill toggles
- Clear ON/OFF labeling with stencil text
- Satisfying visual snap between states

### Static Option Selectors (Known Sets)

For small, static, known-at-render-time option sets (e.g. a frequency selector with fixed choices), use an **indicator radio group**: a set of labeled radio inputs styled as hardware selector switches. Each option is always visible; the selected one is lit.

For dynamic option sets loaded from an API, see **Dynamic Option Selectors** below.

### Dynamic Option Selectors (API-loaded options)

When the option list is populated from an API call and the full set is not known at render time, use the **Fallout terminal screen pattern** rather than an indicator radio group.

The selector is a CRT screen component (bezel, scanlines, vignette) at fixed height, always present in the layout. Screen contents reflect the current state:

- **Loading:** blinking cursor, `FETCHING...` copy, Seafoam (`#7EC8A0`)
- **Loaded:** scrollable list of `> Option Name` rows, each clickable/selectable
- **Selected:** chosen row retains `>` prefix and carries a selection marker; others dim but remain visible
- **Error:** Fire Red (`#C4392F`) text, static (no cursor), error message and recovery hint

This pattern is honest to the physical metaphor: in-universe these are touchscreen terminals, making a clickable list physically plausible. It also scales to any number of options without layout shift.

**Accessibility:** apply `role="listbox"` to the content area, `role="option"` and `aria-selected` to each row. Arrow key + Enter navigation is required. Decorative `>` prefixes must be `aria-hidden`.

**Static, small, known option sets** (e.g. frequency selector) continue to use the indicator radio group pattern. Use the terminal screen pattern only when options are dynamic or not known at render time.

### Split-Flap Message Slots

Use a split-flap message slot for any transient status message — save confirmations, operation results, mode-triggered warnings — where a persistent lamp would be ambiguous to a first-time user.

**States:**
- **Idle:** fixed-size panel filled with hatched or horizontal-rule texture in Faded Green at low opacity. Always present, never empty-looking.
- **Message:** flip animation (`rotateX`, `200ms`, `ease-in-out`) reveals message text. Color follows standard palette conventions (Seafoam = success, Fire Red = failure/warning, Solar Yellow = in-progress).
- **Reset:** transient messages flip back to idle after a 5s timeout. Persistent messages flip back when the triggering condition clears.

**Rules:**
- Container is always fixed height and width — no layout shift on any transition
- `prefers-reduced-motion`: replace flip with `opacity` crossfade (`150ms`)
- Do not use for state that needs to persist indefinitely — use an indicator lamp instead
- Do not use for state the user needs to act on — use an error panel with explicit copy instead

### Dropdowns / Select Inputs

Do not use `<select>` elements or custom dropdown flyouts. Replace all dropdowns with **indicator radio groups**:
- Render all options simultaneously in the layout — never hide options
- Each option: seafoam indicator lamp (lit when selected, dark when not) + stencil label
- Selected state: lamp illuminated (Seafoam `#7EC8A0` with glow), surface inset
- Unselected state: lamp dark (`#1E221E`), surface outset (convex)
- The number of options should be small enough that all fit without scrolling

---

## 6. Layout Principles

- **Panel-based composition**: the UI is a collection of hardware panels, not a flat canvas. Group related controls into distinct physical units with visible borders and depth.
- **Asymmetric but structured**: real hardware is rarely perfectly symmetrical. Allow intentional asymmetry in panel sizing.
- **Density is acceptable**: analog hardware is information-dense. Don't over-whitespace — use it purposefully.
- **Visible fasteners**: decorative screws or rivets at panel corners reinforce the physical metaphor.
- **Rack-mount grid**: use an 8px base grid. Panel heights should snap to multiples of standard rack units (44px = 1U equivalent).
- **Labeling everything**: every control has a label. Labels use stencil or monospace type, uppercase, small size.

---

## 7. Motion & Animation

Analog hardware moves mechanically, not fluidly. Animation should feel **physical and constrained**.

| Interaction | Animation Style | Duration |
|---|---|---|
| Button press | Instant snap down / snap up | `50ms` ease-in, `80ms` ease-out |
| Fader drag | Direct 1:1 tracking, no easing | — |
| Knob rotation | Direct 1:1, no momentum | — |
| VU meter | Fast attack (`30ms`), slow decay (`300ms`) | — |
| Tape reel spin | Continuous rotation, speed-mapped to progress | — |
| Screen flicker | Occasional random opacity pulse | Rare, `20ms` |
| Panel open/close | Mechanical slide or hinge, slight overshoot | `200ms` cubic-bezier |
| Loading state | Tape reel animation or segmented progress bar | — |

**Rules:**
- No bouncy spring animations
- No blur transitions
- No scale-up "pop" effects
- Easing curves should be `ease-in-out` or linear — never `cubic-bezier` with overshoot except for mechanical hinge effects

---

## 8. Iconography

- Icons are **engraved or embossed** into surfaces — not floating glyphs
- Use standard tape/audio iconography: ▶ ■ ◀◀ ▶▶ ⏏ — these are universally understood
- Custom icons should look **stamped or silk-screened**, not illustrated
- Stroke weight: heavy (2–3px equivalent), no thin lines
- No filled flat icons — use outlined with slight bevel treatment
- Icon labels are always present; icons alone are insufficient

---

## 9. Sound Design (Optional but Recommended)

If the product includes UI sounds:
- Button clicks: short, mechanical — think keyboard switches or relay clicks
- Toggle switches: heavier clunk
- Fader movement: subtle tape hiss on drag
- Alerts: single-tone beep, not melodic chimes
- No soft, rounded, or "digital" UI sounds

---

## 10. Do's and Don'ts

### ✅ Do
- Layer textures — real hardware has depth and imperfection
- Use seafoam phosphor glow on active/selected states
- Label every control with stencil-style text
- Design for density — pack controls like a real device
- Add wear and patina subtly (slight discoloration, worn edges)
- Keep screens inside bezels with scanlines
- Use segmented/discrete indicators over smooth gradients
- **Reserve Fire Red strictly for errors and danger states** — never decorative
- **Reserve Solar Yellow strictly for caution/in-progress** — never decorative

### ❌ Don't
- Use flat, shadow-free surfaces
- Use amber, blue, purple, or neon pink as accent colors
- Use neon/saturated greens (`#39FF14` etc.) — the palette is muted and industrial, not cyberpunk
- Use smooth, springy animations
- Use sans-serif proportional fonts as the primary typeface
- Use iOS/Material-style components (pill toggles, FABs, bottom sheets)
- Use full-bleed imagery or photography as backgrounds
- Use gradients that go light-to-dark vertically (this reads as flat, not physical)
- Over-polish — perfection breaks the aesthetic; slight imperfection is intentional
- **Use Fire Red or Solar Yellow outside their designated semantic roles**

---

## 11. Reference Touchstones

Use these as visual anchors when making decisions:

- **Film**: *Alien* (1979) — Nostromo computer terminals; *Blade Runner* (1982) — Voight-Kampff machine; *WarGames* (1983)
- **Hardware**: NASA Apollo/mission control panels, X-10 Graphite Reactor control room, Hanford Site B-Reactor, Tascam 4-track cassette recorders, Ampex reel-to-reel machines, Commodore PET
- **Games**: *Fallout* series (Pip-Boy UI), *Alien: Isolation* (motion tracker, ship terminals)
- **Color theory**: Faber Birren's 1944 Industrial Color Safety Code — functional color, not decorative

---

## 12. Persistent State Panels

Every panel is a fixed physical surface. Controls do not appear or disappear based on state — they are always present, always in the same position.

### Mode-Driven Parameter Panels

When a control section has multiple modes that require different parameter inputs, render **all parameters simultaneously** in a fixed-height panel. Never conditionally mount or unmount inputs based on the selected mode.

Use active / inactive visual state to communicate relevance:

- **Active:** full brightness, interactive, standard recessed input treatment
- **Inactive:** opacity `0.25`–`0.35`, `disabled` attribute, same position and size as active state — nothing moves

Warning indicators follow the same rule: the lamp housing and its label are always present. The lamp illuminates when the relevant mode is selected; the label is always readable.

The guiding principle: a user should be able to read the entire panel and understand all available parameters before touching a single control.

---

*This document is a living reference. When in doubt, ask: "Does this look like it was built, not designed?"*

---

## Summary of Changes (v1.0 → v1.1)

| What changed | v1.0 | v1.1 |
|---|---|---|
| Primary accent | Phosphor Amber `#FFB347` | Seafoam `#7EC8A0` |
| Secondary accent | CRT Green `#39FF14` | Solar Yellow `#C8A84B` |
| Background tint | Warm black `#0D0D0B` | Cool green-black `#0D0F0D` |
| Panel color | Gunmetal `#1C1C1A` | Machinery Gray `#1E221E` |
| Bezel/surface | Aged Plastic `#C8B89A` | Industrial Gray `#4A524A` |
| Inactive surface | *(none)* | Birren Beige `#D4C5A9` |
| Text glow | Amber `#FFB347` | Seafoam `#7EC8A0` |
| VU meter progression | Green → Amber → Red | Seafoam → Solar Yellow → Fire Red |
| Screen tint | Green or amber | Seafoam green |
| Indicator lamps | Amber | Seafoam |
| Color philosophy | Aesthetic/warm | Functional/Birren — each color has a reserved semantic role |
