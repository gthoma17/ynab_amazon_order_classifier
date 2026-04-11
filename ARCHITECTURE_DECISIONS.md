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
| 12 | Cassette Futurism design system | All UI must follow the Cassette Futurism visual language documented in `UI_DESIGN_PRINCIPLES.md`. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#1-persistent-panel-pattern) |
| 13 | Persistent panel pattern | UI panels always occupy a fixed housing; state changes drive content inside a panel, not the panel's existence. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#1-persistent-panel-pattern) |
| 14 | SplitFlapSlot for status messages | Transient save confirmations and warnings use `SplitFlapSlot` (5 s timeout) not conditionally rendered text. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#2-split-flap-slot-as-saveStatus-indicator) |
| 15 | RadioGroup for static option selection; CRT for dynamic | Use `RadioGroup` (indicator radio group) when all options are known ahead of time. Use a CRT terminal panel when options are retrieved dynamically (e.g., YNAB budget list). The budget selector CRT also serves as implicit YNAB token validation — no separate Test YNAB button is needed. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#3-indicator-radio-group-for-mode-selection) |
| 16 | Self-hosted fonts, no external CDN | External font CDNs (Google Fonts) are prohibited; use system monospace fallbacks to preserve offline/privacy guarantees. | [ADR-0003](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md#6-self-hosted--system-fonts-only--no-external-cdn) |

---

## All ADRs

| File | Title | Date | Status |
|---|---|---|---|
| [20260408_cassetteFuturismUiDesignSystem.md](docs/ADRs/20260408_cassetteFuturismUiDesignSystem.md) | Cassette Futurism UI Design System | 2026-04-08 | Accepted |
