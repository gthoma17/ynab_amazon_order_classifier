# ADR-0005: Code Hygiene Tooling (Pre-Commit Hooks + CI Enforcement)

**Date:** 2026-04-06
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #46 — Enforce code hygiene with formatter, linter, pre-commit hooks, and CI gates

---

## Context

No automated formatting or linting existed. Code style inconsistencies accumulated. PRs required manual style review.

---

## Decision

### Kotlin: Spotless + ktlint 1.5.0 (pinned)

`./gradlew spotlessCheck` / `spotlessApply`. Pinned version ensures deterministic output across CI and developer machines.

### TypeScript: Prettier 3.5.3 (exact-pinned) + ESLint

`npm run format:check` / `format`. Exact pin (`"3.5.3"` not `"^3.5.3"`) eliminates spurious diffs from patch releases changing formatting output.

### Git Hooks: Lefthook 1.11.13 (exact-pinned)

**Pre-commit:** format/lint checks (< 10s, fail fast)  
**Pre-push:** full test suite (30s–2min)

Hooks auto-install via `npm ci` (frontend `prepare` script).

### CI Enforcement

New jobs: `kotlin-format`, `frontend-format-lint`. All must pass to merge. CI is the enforcement gate; hooks are developer convenience.

---

## Consequences

- Unformatted code cannot merge (CI fails)
- Developers cannot commit unformatted code without `--no-verify`
- `git blame` no longer shows formatting-only commits
- Spotless/Prettier version upgrades require full-repo format pass

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| ktlint CLI | Spotless integrates naturally with Gradle |
| ESLint `--fix` for formatting | Prettier is opinionated, ESLint is for logic errors |
| Husky | Lefthook is faster (Go binary), declarative YAML |
| Pre-commit hooks for tests | 30s–2min latency makes commits punishing |
