# ADR-0003: Skeuomorphic Industrial Control UI Design System

**Date:** 2026-04-08  
**Status:** Accepted (updated 2026-04-11 to reflect v1.1 changes)  
**Deciders:** Greg Thomas  
**Source:** PR #54 — UI Polish: Cassette Futurism Design System + Layout Overhaul

---

## Context

The initial Budget Sortbot UI was functional but visually generic — unstyled HTML inputs, plain buttons, and `<select>` dropdowns. As the product matured, a decision was made to introduce a cohesive visual language that reflected the app's personality: a self-hosted, low-power, always-on appliance.

The design direction is **Skeuomorphic Industrial Control** — drawn from mid-century industrial control rooms, Faber Birren's 1944 functional color safety code, and institutions like the X-10 Graphite Reactor control room and NASA Apollo mission control. The core principle is that the UI should feel like physical hardware: switches and indicators are always visible, panels never grow or shrink on state change, and status is communicated through lamp states rather than appearing and disappearing UI elements.

> **Note:** Early commits and some PR titles use the name "Cassette Futurism." This was renamed to "Skeuomorphic Industrial Control" in v1.1 to better reflect that the lineage runs through Birren and real control facilities — not film props.

All design decisions are documented in `UI_DESIGN_PRINCIPLES.md`. This ADR captures the architectural decisions embedded in the implementation.

---

## Decisions

### 1. Persistent Panel Pattern

**Decision:** UI panels must always occupy a fixed housing with a predetermined size. State changes drive the content inside a panel, not the existence of the panel itself.

**Rationale:** In physical hardware UIs, the housing (bezel, rack panel, enclosure) exists regardless of operating state. Adopting this metaphor means layout never shifts when state changes. The prior pattern of conditionally rendering success/error messages caused layout shift and was inconsistent with the hardware metaphor.

**Implementation:** 
- `.cf-indicator-panel` has `min-height: 48px`
- `.cf-crt` has `min-height: 120px`
- Save confirmations use always-rendered `SplitFlapSlot` components rather than conditionally-rendered text
- Budget selector CRT always renders; content transitions through `AWAITING TOKEN → FETCHING BUDGETS → SELECT BUDGET / errors`

---

### 2. Split-Flap Slot as Save/Status Indicator

**Decision:** Transient status messages (save confirmations, warnings) are displayed using `SplitFlapSlot` components, not conditionally rendered text or `DashboardLamp` components.

**Rationale:** The split-flap display is a fitting metaphor for a system that processes items in batch: it communicates state transitions without adding or removing DOM elements. The housing is always present (persistent panel pattern); only the displayed message changes.

**Timeout:** Transient messages (save confirmations) flip back to idle after a **5 second** timeout (`setTimeout(..., 5000)` in `ConfigView.tsx`). Persistent messages (warnings triggered by mode-based conditions) clear when the triggering condition clears.

**Test IDs:** `SplitFlapSlot` exposes a `slotTestId` prop for the container and a `messageTestId` prop for the message span. E2E tests assert on the `messageTestId` element (present in DOM only when a message is displayed) rather than a `data-lit` attribute.

---

### 3. Indicator Radio Group for Mode Selection

**Decision:** `<select>` dropdown elements must not be used for mode/frequency selection. Use a `RadioGroup` component where all options are always visible, the selected option has its **Neon Green lamp** (`#39FF14`) lit and surface inset, and unselected options have their lamp dark (`rgba(57,255,20,0.12)` tint only) and surface convex.

**Rationale:** A `<select>` dropdown hides options behind an interaction, which is inconsistent with the always-visible physical panel metaphor. All available modes should be scannable at a glance, the same way a physical selector switch presents all positions on the face of a panel.

**Accessibility:** Each `.cf-radio-option` has a `:focus-within` style that adds an amber border and glow when the hidden native radio input receives keyboard focus, ensuring keyboard users have a visible indicator.

**Scope:** This pattern applies to all mode/type selections. `UI_DESIGN_PRINCIPLES.md` documents this rule in Section 14.

---

### 4. Split Save Operations with Scoped API Payloads

**Decision:** The Configuration view's credential panel is split into two independent save operations: **Save Signal Sources** (YNAB Token, YNAB Budget ID, FastMail API Token) and **Save AI Engine** (Gemini Key). Each sends only the fields belonging to its section to `PUT /api/config/keys`.

**Rationale:** The backend's `PUT /api/config/keys` performs a partial update — it only updates fields that are present in the request body; omitted fields are left unchanged. If a single "Save All" button sent all four fields, a user who had saved their Gemini key but then cleared the field to set a new Signal Sources value would inadvertently clear the stored Gemini key on save. Scoping each save operation to its own field set prevents this class of accidental data loss.

**Implementation:** `handleSaveSignalSources` builds a request body with only `ynabToken`, `ynabBudgetId`, and `fastmailApiToken`. `handleSaveAiEngine` builds a request body with only `geminiKey`. Neither function references the other section's state.

---

### 5. Budget Selector as Implicit YNAB Token Validation

**Decision:** There is no separate "Test YNAB" button or probe indicator panel in the Signal Sources sub-panel. YNAB token validity is implicitly validated by the budget selector: if the token is valid, budgets load; if invalid, the CRT shows an error state.

**Rationale:** The budget selector must make a live YNAB API call anyway to populate the budget list. A redundant "Test YNAB" button would duplicate that call and add UI complexity. The budget selector CRT provides richer feedback (error message from the API response) than a simple pass/fail lamp.

---

### 6. Self-Hosted / System Fonts Only — No External CDN

**Decision:** The UI must not load fonts (or any other resources) from external CDNs such as `fonts.googleapis.com` or `fonts.gstatic.com`. Font families are declared using system monospace fonts as fallbacks only.

**Rationale:** Budget Sortbot is designed for self-hosted, potentially offline deployments (home router, NAS, Raspberry Pi without internet access). Loading external fonts breaks the UI in offline environments, introduces a privacy risk (external request on every page load), and contradicts the in-app copy that states "No data is sent anywhere." System monospace fonts (Courier Prime, IBM Plex Mono, Share Tech Mono, VT323, Courier New, Liberation Mono, DejaVu Sans Mono) provide adequate visual fidelity without any network dependency.

**Implementation:** `frontend/index.html` contains a `<style>` block with a `font-family` declaration listing these system fonts in priority order. Any `<link>` tags to Google Fonts APIs are prohibited.

---

### 7. Standardized Credential Input Styling

**Decision:** All credential text inputs (YNAB Token, FastMail API Token, Gemini Key) share the `.cf-credential-input` CSS class, which uses an explicit opaque background colour (`#0d0d0c`) and a fixed height of `34px`.

**Rationale:** Without an opaque absolute colour, a `background: rgba(0,0,0,0.45)` input blends with its parent panel differently depending on nesting depth (e.g., the YNAB Token input is two panels deep while the Gemini Key input is one panel deep). This causes pixel-level inconsistency. An explicit absolute colour guarantees identical appearance regardless of nesting context.

---

### 8. ARIA listbox Pattern for Budget Terminal

**Decision:** The budget terminal uses an ARIA `listbox` / `option` role hierarchy. The `aria-activedescendant` attribute on the listbox tracks the currently keyboard-highlighted option (updated on ArrowUp/ArrowDown), not just the selected option.

**Rationale:** The ARIA listbox pattern requires `aria-activedescendant` to point to the option that currently has "focus" during keyboard navigation. Setting it to only the selected option means screen readers announce nothing when the user navigates with arrow keys without selecting. Tracking the highlighted index separately from the selected budget ID ensures correct screen reader announcement during navigation.

**Implementation:** A `budgetListboxActiveDescendant` computed variable derives the active descendant ID from `displayBudgets[highlightedBudgetIndex]?.id`, falling back to `keys.ynabBudgetId` when no navigation has occurred.

---

### 9. Wall Seafoam Body Background — No Text on the Wall

**Decision:** The `body` background is `#63827A` (Wall Seafoam) on all pages. No text, labels, or readable content may be rendered directly on this surface.

**Rationale:** Wall Seafoam sits in a mid-value dead zone — no palette color achieves WCAG AA contrast against it. This is a hard constraint, not a tuning problem. The physical metaphor reinforces it: in a real control room, nothing is painted on the wall. Content lives on panels, placards, and bezels. `#63827A` is a direct colormeter reading from a physical seafoam-painted wall; it is the canonical wall reference.

**Implementation:** Every page header (e.g., "Configuration", "Category Rules") and every navigational label must be wrapped in a `.cf-panel` or `.cf-view-header` container with Machinery Gray backing. The layout structure ensures no `<h1>` or explanatory text floats on the raw `body` background.

---

### 10. Panel and Bezel Surfaces Must Be Neutral Gray — Never Green-Tinted

**Decision:** All panel surfaces (`#1E221E` Machinery Gray) and bezels (`#4A524A` Industrial Gray) must be neutral. Green tints are prohibited on panel/bezel surfaces.

**Rationale:** The entire contrast mechanism of the design system depends on a recessive, neutral panel substrate against which Seafoam and Neon Green controls can fire with maximum pre-attentive signal strength. If the panel carries a green tint and the primary accent is also green, controls disappear into the background — the system is inverted. Machinery Gray and Industrial Gray are structurally neutral by definition; Seafoam/green belongs to screens, walls, and active control states only.

**Rule:** If a surface is a panel, bezel, or equipment housing and it has any green cast, it is wrong. Use `#1E221E` or `#4A524A` exactly.

---

### 11. Button Semantic Color Roles

**Decision:** Button face colors encode semantic meaning using Birren's functional color hierarchy. A button's color must match its semantic role — not the section's accent.

| Role | Face | Label |
|---|---|---|
| Primary / confirm | Seafoam `#7EC8A0` | Reactor Black `#0D0F0D` |
| Caution / irreversible | Solar Yellow `#C8A84B` | Reactor Black `#0D0F0D` |
| Destructive / emergency | Fire Red `#C4392F` | Phosphor White `#E8F0E8` |
| Neutral / secondary | Industrial Gray `#4A524A` | Phosphor White `#E8F0E8` |
| Disabled / inactive | Birren Beige `#D4C5A9` at 30% opacity | — |

**Rationale:** Buttons that are close in value to the panel surface cannot be pre-attentively scanned — the operator must read the panel to find the control. Functional color rules ensure every active control has a genuine brightness and hue jump against the `#1E221E` panel. Only disabled buttons may be panel-adjacent (signaling non-interactivity).

---

### 12. Neon Green (`#39FF14`) for Lamps; Seafoam (`#7EC8A0`) for Accents — Two Distinct Tokens

**Decision:** Physical indicator lamps (lit state) use `--cf-lamp-success: #39FF14` with glow `rgba(57,255,20,…)`. Their unlit/dim state is `--cf-lamp-success-dim: rgba(57,255,20,0.12)`. The Seafoam accent token `--cf-green: #7EC8A0` is reserved for text glows, UI highlights, and screen tints — never lamp backgrounds.

**Rationale:** Sharing a single green token for both lamps and accents created semantic confusion: a "success" UI element (a label glow) and a "success" lamp (a physical bulb) looked identical but carried different meanings and used different contrast targets. Splitting the tokens makes each usage explicit and prevents Neon Green from leaking outside lamp elements (the Don't rule in `UI_DESIGN_PRINCIPLES.md` Section 10).

**Applies to:** `SplitFlapSlot` with `data-color="green"` and all CRT terminal text use `#39FF14`. Radio lamp selected state, param lamp active, dashboard lamp bulb lit all use `#39FF14`.

---

### 13. Rocker Toggles — iOS Pill Toggles Prohibited

**Decision:** Toggle inputs (e.g., "Include sync logs", "Include app logs") must use a rectangular OFF|ON rocker switch, not an iOS-style pill slider.

**Rationale:** Pill toggles are explicitly listed under Don'ts in `UI_DESIGN_PRINCIPLES.md` Section 10. They are iOS/Material-style components that contradict the industrial hardware metaphor. A two-section rocker with visible position states (unchecked = OFF half pressed/inset, ON half raised; checked = ON half pressed) matches breaker-panel and vintage amp toggle language.

---

## Consequences

- `UI_DESIGN_PRINCIPLES.md` is the living specification for the Skeuomorphic Industrial Control design system and must be updated whenever a new pattern is introduced or an existing rule changes.
- All future UI additions should follow the persistent panel pattern — no conditionally mounted major components.
- `<select>` dropdowns are prohibited for mode selection; use `RadioGroup` instead.
- External font or resource CDNs are prohibited.
- Split-save operations with scoped payloads are the required pattern for any configuration panel that has multiple independently-saveable sections.
