# Architecture Decisions

This document is a quick-reference summary of the Architecture Decision Records (ADRs) for Budget Sortbot.
**Read this before starting any story.** Follow links to the full ADR for deeper context on any decision.

---

## How to Use This Document

- Scan the table below before beginning a new task to check whether your planned approach conflicts with a prior decision.
- Follow the link in the **ADR** column to read the full rationale for decisions that are relevant to your story.
- If your story introduces a new architectural decision, create a new ADR file under `docs/ADRs/` using the filename pattern `YYYYMMDD_shortCamelCaseTitle.md` and add a summary row to this table.

---

## Decision Summary

| # | Decision | Summary | ADR |
|---|---|---|---|
| 1 | Cassette Futurism design system | All UI must follow the Cassette Futurism visual language documented in `UI_DESIGN_PRINCIPLES.md`. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#1-persistent-panel-pattern) |
| 2 | Persistent panel pattern | UI panels always occupy a fixed housing; state changes drive content inside a panel, not the panel's existence. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#1-persistent-panel-pattern) |
| 3 | SplitFlapSlot for status messages | Transient save confirmations and warnings use `SplitFlapSlot` (5 s timeout) not conditionally rendered text. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#2-split-flap-slot-as-saveStatus-indicator) |
| 4 | RadioGroup replaces `<select>` | Mode/frequency selection uses indicator radio groups (all options always visible, amber lamp on selection). No `<select>` dropdowns for mode selection. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#3-indicator-radio-group-for-mode-selection) |
| 5 | Split save with scoped payloads | Save operations only send the fields in their own section to `PUT /api/config/keys` to prevent accidentally clearing unrelated credentials. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#4-split-save-operations-with-scoped-api-payloads) |
| 6 | Budget selector as YNAB probe | No separate "Test YNAB" button; YNAB token validity is implicitly validated by the budget-loading step. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#5-budget-selector-as-implicit-ynab-token-validation) |
| 7 | Self-hosted fonts, no external CDN | External font CDNs (Google Fonts) are prohibited; use system monospace fallbacks to preserve offline/privacy guarantees. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#6-self-hosted--system-fonts-only--no-external-cdn) |
| 8 | Opaque absolute colour for credential inputs | `.cf-credential-input` uses `background: #0d0d0c` to ensure pixel-identical appearance regardless of panel nesting depth. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#7-standardized-credential-input-styling) |
| 9 | ARIA listbox with `aria-activedescendant` tracking | Budget selector listbox tracks the keyboard-highlighted option in `aria-activedescendant`, not just the selected budget. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#8-aria-listbox-pattern-for-budget-terminal) |

---

## All ADRs

| File | Title | Date | Status |
|---|---|---|---|
| [20260408_cassetteFuturismUiDesignSystem.md](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md) | Cassette Futurism UI Design System | 2026-04-08 | Accepted |
