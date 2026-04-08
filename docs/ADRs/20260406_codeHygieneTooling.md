# ADR-0005: Code Hygiene Tooling (Spotless, Prettier, ESLint, Lefthook Pre-Commit Hooks)

**Date:** 2026-04-06
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #46 — Enforce code hygiene with formatter, linter, pre-commit hooks, and CI gates

---

## Context

Through v1.0.0-rc, the project had no automated formatting or linting enforcement. Code style inconsistencies (wildcard imports, mixed quote styles, inconsistent indentation) accumulated. PRs required manual style review. Agents working across multiple sessions introduced style drift.

PR #46 introduced formatter/linter tooling with Git hook enforcement and CI gates to prevent style issues from entering the commit graph.

---

## Decision

### Kotlin Formatting — Spotless + ktlint

**Tool:** Spotless Gradle plugin (v6.25.0) with ktlint 1.5.0 (pinned for deterministic output)

**Configuration:**
```kotlin
spotless {
    kotlin {
        target("**/*.kt")
        ktlint("1.5.0")
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint("1.5.0")
    }
}
```

**Commands:**
- `./gradlew spotlessCheck` — fails build if any file diverges from ktlint format
- `./gradlew spotlessApply` — auto-formats all Kotlin source in-place

**Enforced rules:**
- No wildcard imports (`import org.junit.jupiter.api.Assertions.*` → explicit imports)
- Consistent indentation (4 spaces)
- Max line length (ktlint default: 120 chars)

### TypeScript/React Formatting — Prettier

**Tool:** Prettier 3.5.3 (exact-pinned devDependency, no range specifier)

**Configuration (`.prettierrc`):**
```json
{
  "singleQuote": true,
  "semi": false,
  "trailingComma": "all",
  "printWidth": 100
}
```

**Commands:**
- `npm run format:check` — exits non-zero if any file needs formatting
- `npm run format` — auto-formats all TypeScript/TSX in-place

**Exact pinning rationale:** `"prettier": "3.5.3"` (not `"^3.5.3"`) eliminates supply chain risk from transitive updates that could change formatting output and cause spurious diffs.

### TypeScript Linting — ESLint

**Tool:** ESLint with React plugin (inherited from Vite React template)

**Command:** `npm run lint`

**Pre-existing config violations fixed in PR #46:**
- `react-hooks/set-state-in-effect` errors in `ConfigView.tsx` (state updates inside `useEffect` without dependency tracking)
- Fixed by moving state initialisation to lazy `useState` initializers and replacing effect-based state updates with render-time derived variables

### Git Hooks — Lefthook

**Tool:** Lefthook 1.11.13 (exact-pinned devDependency in `frontend/package.json`)

**Installation:** Auto-installed via `npm ci` (frontend `prepare` script runs `npx lefthook install`)

**Hook configuration (`lefthook.yml` at repo root):**

**Pre-commit:**
- `kotlin-format` — `./gradlew spotlessCheck` (fail fast on unformatted Kotlin)
- `ts-format` — `npm run format:check` (fail fast on unformatted TypeScript)
- `ts-lint` — `npm run lint` (fail fast on ESLint errors)

**Pre-push:**
- `backend-tests` — `./gradlew test`
- `frontend-tests` — `npm test` (Vitest unit tests)
- `journey-test` — `npm run test:e2e` (Playwright E2E)

**Rationale for hook placement:**
- **Pre-commit** for format/lint — fast checks (< 10s); reject bad code before it enters the commit graph
- **Pre-push** for tests — slow checks (30s–2min); pre-commit would make the commit flow punishing

### CI Enforcement

**New GitHub Actions jobs:**
- `kotlin-format` — runs `./gradlew spotlessCheck` on every PR; fails if any Kotlin file is unformatted
- `frontend-format-lint` — runs `npm run format:check && npm run lint` on every PR; fails if TypeScript is unformatted or has ESLint errors

Both jobs run in parallel with the existing `test` and `e2e-tests` jobs. All must pass to merge.

---

## Rationale

**Why Spotless instead of ktlint CLI?**
- Spotless is a Gradle plugin; integrates naturally with `./gradlew build`
- ktlint CLI requires a separate install step and manual wiring into CI
- Spotless supports multiple formatters (could add `prettier` for JSON/YAML if needed)

**Why ktlint 1.5.0 pinned instead of latest?**
- Spotless uses the ktlint version specified in `build.gradle.kts`; unpinned would float to the latest ktlint release
- ktlint formatting rules can change between versions; pinning ensures deterministic output across CI runs and developer machines

**Why Prettier instead of ESLint's `--fix` for formatting?**
- ESLint is a linter (correctness rules); Prettier is a formatter (style rules)
- Prettier has opinionated defaults and zero configuration options for contentious debates (tabs vs. spaces, quote style)
- ESLint + Prettier is the industry-standard React stack (Prettier for format, ESLint for logic errors)

**Why exact-pin Prettier instead of `^3.5.3`?**
- Prettier maintainers explicitly recommend exact pinning to avoid spurious diffs when patch releases change formatting output
- `^3.5.3` would auto-upgrade to `3.5.4`, `3.6.0`, etc., on `npm install` — could cause CI failures if formatting rules change

**Why Lefthook instead of Husky?**
- Lefthook is a single binary (Go); Husky requires Node.js and shell scripts
- Lefthook config is YAML (declarative); Husky is shell scripts (imperative)
- Lefthook has built-in parallelism and skip conditions; Husky requires manual `concurrently` wiring

**Why pre-commit for format/lint instead of pre-push?**
- Format/lint failures are fixable in < 10 seconds (`./gradlew spotlessApply`, `npm run format`)
- Allowing unformatted code into the commit graph creates noisy `git blame` and pollutes history
- Pre-commit hooks are the industry standard for style enforcement (Husky, lint-staged, etc.)

**Why pre-push for tests instead of pre-commit?**
- Test suite takes 30s–2min (backend unit + frontend unit + Playwright E2E)
- Pre-commit hooks that take > 10s are punishing; developers bypass them with `--no-verify`
- Pre-push is late enough to catch test failures before CI, early enough to avoid "fix CI" commits

**Why CI enforcement on top of pre-commit hooks?**
- Git hooks can be bypassed with `--no-verify`
- CI is the enforcement gate; hooks are a developer convenience to fail fast locally

---

## Consequences

- All Kotlin code must pass `./gradlew spotlessCheck` before merge; PRs with unformatted code fail CI
- All TypeScript code must pass `npm run format:check && npm run lint` before merge; PRs with unformatted or linted code fail CI
- Developers cannot commit unformatted code without `--no-verify`; cannot push failing tests without `--no-verify`
- `git blame` no longer shows formatting-only commits; all diffs are semantic changes
- New contributors must run `npm ci` in the `frontend/` directory to install Lefthook; the `prepare` script auto-installs hooks
- Any story that changes Kotlin/TypeScript code must pass format/lint checks before commit
- Spotless/Prettier/ESLint version upgrades require a full-repo format pass to avoid spurious CI failures

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| ktlint CLI instead of Spotless | Requires separate install; no Gradle integration; Spotless is the idiomatic Gradle approach |
| ESLint `--fix` for formatting | ESLint is a linter, not a formatter; Prettier has better opinionated defaults |
| Husky instead of Lefthook | Husky requires shell scripts; Lefthook is declarative YAML and faster (Go binary) |
| Pre-commit hooks for tests | 30s–2min latency makes commits punishing; developers would bypass with `--no-verify` |
| CI-only enforcement (no Git hooks) | Hooks fail fast locally; CI is slow (3–5min end-to-end); hooks reduce CI feedback loop |
| Detekt for Kotlin linting | Spotless + ktlint covers formatting; Detekt is a static analysis tool (orthogonal); adding both would be heavyweight for v1 |
| `@typescript-eslint/strict` config | Vite React template uses a lighter config; strict mode flags valid React patterns as errors |
