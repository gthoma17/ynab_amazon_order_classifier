# ADR-0003: Cassette Futurism UI Design System

**Date:** 2026-04-08  
**Status:** Accepted  
**Deciders:** Greg Thomas  
**Source:** PR #54 — UI Polish: Cassette Futurism Design System + Layout Overhaul

---

## Context

The initial Budget Sortbot UI was functional but visually generic — unstyled HTML inputs, plain buttons, and `<select>` dropdowns. As the product matured, a decision was made to introduce a cohesive visual language that reflected the app's personality: a self-hosted, low-power, always-on appliance.

The "Cassette Futurism" aesthetic — inspired by industrial control panels, CRT monitors, and retro-futurist hardware interfaces — was chosen as the design direction. The core principle is that the UI should feel like physical hardware: switches and indicators are always visible, panels never grow or shrink on state change, and status is communicated through lamp states rather than appearing and disappearing UI elements.

All design decisions from this PR are documented in `UI_DESIGN_PRINCIPLES.md`. This ADR captures the architectural decisions embedded in the implementation.

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

**Decision:** `<select>` dropdown elements must not be used for mode/frequency selection. Use a `RadioGroup` component where all options are always visible, the selected option has its amber lamp lit and surface inset, and unselected options have their lamp dark and surface convex.

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

## Consequences

- `UI_DESIGN_PRINCIPLES.md` is the living specification for the Cassette Futurism design system and must be updated whenever a new pattern is introduced or an existing rule changes.
- All future UI additions should follow the persistent panel pattern — no conditionally mounted major components.
- `<select>` dropdowns are prohibited for mode selection; use `RadioGroup` instead.
- External font or resource CDNs are prohibited.
- Split-save operations with scoped payloads are the required pattern for any configuration panel that has multiple independently-saveable sections.
