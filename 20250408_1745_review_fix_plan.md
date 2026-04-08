# Review Fix Plan — 2025-04-08 17:45

Tracking the tasks from comment #4073817473.

## Tasks

- [ ] **1. Split-flap overflow** — `⚠  NOT RECOMMENDED FOR PRODUCTION · DEV / TEST ONLY` overflows the SplitFlapSlot window.
  - Root cause: `.cf-splitflap { max-width: 400px; overflow: hidden }`. The message is 51 chars (~612 px needed).
  - Fix: In `.cf-sync-warning .cf-splitflap` override `max-width: none`. Also remove the hard `padEnd(20)` cap in `SplitFlapSlot.tsx` — or at minimum change it to `padEnd(Math.max(20, message.length))`.

- [ ] **2. HOUR / MIN radio label overlap** — labels overlap the small indicator lamps.
  - Root cause: `.cf-radio-group--time .cf-radio-option` uses `justify-content: center` but the lamp+gap+label exceed 48 px column width in some cases.
  - Fix: reduce label font-size in time-group options and/or tighten the `gap` between lamp and label.

- [ ] **3. IndicatorPanel text → SplitFlapSlot** — replace the rotary readout in `IndicatorPanel.tsx` with `SplitFlapSlot`.
  - New structure: indicator light (`.cf-lamp-housing`) + `<SplitFlapSlot message={...} color={...} />`.
  - `color`: `'success'` → green, `'error'` → red, else green.
  - Idle / standing-by state: pass `null` so the slot shows its idle dashes.
  - Remove the old rotary animation logic from the component.
  - Remove `.cf-lamp-body`, `.cf-lamp-readout`, `.cf-rotary-segment` from the component; keep those CSS classes only if still used elsewhere.

- [ ] **4. Dry run results fixed height** — height changes when results arrive.
  - Fix: give the dry run `.cf-crt` a fixed height (e.g. `min-height: 320px`) with `overflow-y: auto`. The standing-by and results states occupy the same rectangle.

- [ ] **5. CategoryRulesView → CRT terminal display** — make it look like the budget selector.
  - Create a new `CrtPanel.tsx` component that wraps children in `.cf-crt` styling.
  - Use `CrtPanel` in `CategoryRulesView.tsx` for loading / error / loaded-but-empty / loaded-with-table states.
  - Use `CrtPanel` in `ConfigView.tsx` for the budget selector and dry run sections (already use `.cf-crt` directly; switch to component).

- [ ] **6. GetHelpView status messages → SplitFlapSlot**
  - `"✓ Logs inserted"` message → `<SplitFlapSlot message={logsInserted ? "LOGS INSERTED" : null} />`
  - `"Sensitive values ... were removed"` message → `<SplitFlapSlot message={sanitized ? "SENSITIVE VALUES REDACTED" : null} />`

- [ ] **7. IndicatorButton component + always-visible Insert Logs button**
  - Create `IndicatorButton.tsx`: a button with a small indicator lamp (`data-active` when enabled).
  - Lamp is lit when the button is enabled, off when disabled.
  - Replace the conditional `{logsRequested && <button>Insert Logs</button>}` block with `<IndicatorButton ... disabled={!logsRequested || isInsertDisabled} />`.
  - The button is always rendered; it enters disabled+lamp-off state when `logsRequested` is false.

## Order of execution

1. Create `IndicatorButton.tsx` and `CrtPanel.tsx` components + CSS.
2. Fix split-flap overflow (CSS + SplitFlapSlot.tsx).
3. Fix HOUR/MIN radio label overlap (CSS).
4. Refactor `IndicatorPanel.tsx` to use `SplitFlapSlot`.
5. Refactor `CategoryRulesView.tsx` to use `CrtPanel`.
6. Fix dry run CRT fixed height.
7. Update `GetHelpView.tsx` for split-flap status messages + `IndicatorButton`.
8. Run `npm test` + `npm run lint` to confirm nothing broken; update unit tests if needed.
