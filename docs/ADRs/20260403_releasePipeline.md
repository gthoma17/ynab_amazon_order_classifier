# ADR-0006: Automated Release Pipeline (Multi-Arch Docker to GHCR)

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #18 — Phase 6: Automated multi-arch release pipeline to GHCR

---

## Context

No automated path existed from merged code to distributable artifact. Users on different hardware (Pi 3 = `arm/v7`, Pi 4/5 = `arm64`, x86 = `amd64`) had to build from source.

---

## Decision

### Trigger: `workflow_run` on CI Success

Release fires automatically when CI completes successfully on `prod` branch. Gated on `github.event.workflow_run.conclusion == 'success'` — failing tests block the release entirely.

### Versioning: Auto-Patch-Bump

Reads latest `v*.*.*` tag, bumps patch, creates new tag. Bootstraps at `v1.0.0` if no prior tag. Tag/release creation is idempotent (safe to re-run after partial failure).

### Multi-Arch: BuildKit with `--platform=$BUILDPLATFORM`

Three platforms: `amd64`, `arm64`, `arm/v7`. Only the final JRE layer runs under QEMU — build stages (Node, Gradle) use `--platform=$BUILDPLATFORM` to compile natively on amd64 GitHub Actions runner:

```dockerfile
FROM --platform=$BUILDPLATFORM node:20-alpine AS frontend-build
FROM --platform=$BUILDPLATFORM gradle:8-jdk17 AS backend-build
FROM eclipse-temurin:17-jre-jammy AS runtime  # ← QEMU only here
```

Compiling under QEMU is 5–10× slower; native compilation for build layers is critical for acceptable CI times.

### Base Image: Ubuntu over Alpine

`eclipse-temurin:17-jre-jammy` (Ubuntu) chosen over Alpine for `linux/arm/v7` support. Alpine's ARM/v7 image is unofficial and flaky. ~20 MB size increase is acceptable for reliability.

---

## Consequences

- Every `main → prod` merge triggers automatic patch release
- Multi-arch images work natively on Pi 3 (`arm/v7`), Pi 4/5 (`arm64`), x86 (`amd64`)
- Release notes include `docker pull` + `docker run` commands
- GHCR package must be toggled to Public manually after first push

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Docker Hub | Pull rate limits on free tier; GHCR has tighter GitHub integration |
| Alpine JRE | ARM/v7 support unofficial; Ubuntu has first-class ARM support |
| Single-arch (`amd64` only) | Pi users would run under QEMU emulation (5–10× slower) |
| `push` trigger on `prod` | Would race with CI; could release before tests finish |
