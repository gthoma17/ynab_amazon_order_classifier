# ADR-0006: Automated Release Pipeline (Multi-Arch Docker to GHCR)

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #18 — Phase 6: Automated multi-arch release pipeline to GHCR

---

## Context

PLAN.md Phase 6 specified multi-arch Docker deployment but did not define a release automation strategy. Through Phase 5, users had to manually build the Docker image from source. There was no versioning scheme, no published artifact, and no path from merged code to a distributable image.

Users on different hardware (Raspberry Pi 3 = `arm/v7`, Pi 4/5 = `arm64`, x86 desktops = `amd64`) needed native images to avoid QEMU emulation overhead.

---

## Decision

### Release Trigger

**`workflow_run` on CI completion on `prod` branch.** The release workflow fires automatically when the CI workflow completes successfully on the `prod` branch. Gated on `github.event.workflow_run.conclusion == 'success'` — a failing test suite blocks the release entirely.

```yaml
on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]

jobs:
  release:
    if: |
      github.event.workflow_run.conclusion == 'success' &&
      github.event.workflow_run.head_branch == 'prod'
```

**Rationale:** `main` is the integration branch; `prod` is the release branch. Merging `main → prod` triggers CI, then release.

### Versioning

**Semantic versioning with automatic patch bumps.** The workflow reads the latest `v*.*.*` tag, bumps the patch version, and creates a new tag. Bootstraps at `v1.0.0` if no prior tag exists.

```bash
LATEST_TAG=$(git tag -l 'v*.*.*' --sort=-v:refname | head -n1)
if [ -z "$LATEST_TAG" ]; then
  NEW_VERSION="v1.0.0"
else
  # Bump patch: v1.2.3 → v1.2.4
  NEW_VERSION=$(echo $LATEST_TAG | awk -F. '{print $1"."$2"."$3+1}')
fi
```

**Manual major/minor bumps:** Push a `v2.0.0` tag manually before merging to `prod` to override the auto-bump.

**Idempotency:** Tag creation (`git tag -a`) and release creation (`gh release create`) both check for existence before creating. Safe to re-run after partial failure.

### Multi-Arch Docker Build

**Three platforms:** `linux/amd64`, `linux/arm/v7`, `linux/arm64`

**BuildKit with QEMU:** Uses Docker Buildx with QEMU for cross-compilation. Only the final runtime stage (Stage 3, JRE) runs under QEMU — Stages 1 & 2 (Node, Gradle) use `--platform=$BUILDPLATFORM` to compile natively on the amd64 GitHub Actions runner.

```dockerfile
FROM --platform=$BUILDPLATFORM node:20-alpine AS frontend-build
FROM --platform=$BUILDPLATFORM gradle:8-jdk17 AS backend-build
FROM eclipse-temurin:17-jre-jammy AS runtime  # ← QEMU only here
```

**Rationale:** Compiling TypeScript/Kotlin under QEMU is 5–10× slower than native. Only the JRE layer needs multi-arch; build layers are platform-agnostic.

**Base image choice:** `eclipse-temurin:17-jre-jammy` (Ubuntu) over Alpine for broadest platform support. Alpine's `linux/arm/v7` image is not officially maintained by Eclipse Temurin.

### Image Tags

**Two tags per release:**
- `ghcr.io/gthoma17/budget-sortbot:<version>` (e.g., `v1.0.0`)
- `ghcr.io/gthoma17/budget-sortbot:latest`

Both tags are multi-arch manifests — Docker automatically pulls the correct image for the host architecture.

### GitHub Release

**Auto-generated release notes with `docker pull` command prepended.**

```bash
gh release create $NEW_VERSION \
  --title "$NEW_VERSION" \
  --notes "$(cat <<EOF
## Docker Image

\`\`\`bash
docker pull ghcr.io/gthoma17/budget-sortbot:$NEW_VERSION
docker run -d -p 8080:8080 -v /opt/budget-sortbot/data:/app/data ghcr.io/gthoma17/budget-sortbot:$NEW_VERSION
\`\`\`

---

$(gh release view --json body -q .body || echo 'Initial release')
EOF
)" \
  --generate-notes
```

Users can copy-paste the `docker pull` + `docker run` commands directly from the release page without reading docs.

### Layer Caching

**GitHub Actions cache for BuildKit.** `docker/build-push-action` caches layer blobs in GHA cache storage, speeding up subsequent builds (especially for the heavyweight Gradle dependency resolution layer).

```yaml
- uses: docker/build-push-action@4d04...
  with:
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

### Concurrency

**No cancellation on release workflows.** `cancel-in-progress: false` — a release mid-flight is never abandoned. If two releases are triggered in quick succession (e.g., two `main → prod` merges), they queue sequentially.

```yaml
concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false
```

---

## Rationale

**Why `workflow_run` on `prod` instead of `push` to `prod`?**
- `workflow_run` ensures CI completes successfully before the release fires
- `push` to `prod` would start the release immediately, potentially before tests finish
- The `conclusion == 'success'` gate prevents releases with failing tests

**Why automatic patch bumps instead of manual version tags?**
- v1 is pre-1.0 feature work; every `main → prod` merge is a patch release
- Manual version bumps would add friction to the release process
- Major/minor bumps can be done manually by pushing a `v2.0.0` tag before merge

**Why `--platform=$BUILDPLATFORM` for build stages?**
- Gradle + npm dependency resolution is platform-agnostic (JARs, npm packages are architecture-neutral)
- Compiling under QEMU is slow; native compilation on amd64 GitHub runners is 5–10× faster
- Only the final JRE layer needs multi-arch (native binaries)

**Why `eclipse-temurin:17-jre-jammy` instead of Alpine?**
- Alpine `linux/arm/v7` support is unofficial and flaky (missing glibc compatibility layers)
- Ubuntu (`jammy`) has first-class ARM support from Canonical
- JRE image size difference (Alpine vs. Ubuntu) is ~20 MB — acceptable tradeoff for reliability

**Why GHCR instead of Docker Hub?**
- GHCR is tightly integrated with GitHub (same auth, same org/repo namespace)
- Docker Hub free tier has pull rate limits (100 pulls/6h for anonymous users)
- GHCR public images have no pull rate limits

**Why idempotent tag/release creation?**
- Release workflows can fail mid-flight (network error, GHCR outage)
- Re-running a failed workflow should not create duplicate tags/releases
- `gh release create --verify-tag` checks for existence before creating

**Why `cancel-in-progress: false`?**
- Cancelling a release mid-flight could leave partial artifacts (tag exists, image not pushed)
- Queuing releases sequentially ensures each completes fully
- Two rapid `main → prod` merges are rare; the queue is not a bottleneck

---

## Consequences

- Every merge from `main` to `prod` triggers an automatic patch release (v1.0.0 → v1.0.1 → v1.0.2)
- Users can `docker pull ghcr.io/gthoma17/budget-sortbot:latest` without building from source
- Multi-arch images work natively on Pi 3 (`arm/v7`), Pi 4/5 (`arm64`), and x86 desktops (`amd64`)
- Release notes include copy-paste `docker pull` + `docker run` commands
- Failed releases (network errors, GHCR outage) can be re-run via Actions UI without duplicate tags
- The `:latest` tag always points to the most recent release; pinning to a specific version tag (`:v1.0.0`) is recommended for production
- GitHub Container Registry package must be toggled to **Public** manually after the first push (Admin → Packages → budget-sortbot → Settings)

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Docker Hub instead of GHCR | Pull rate limits on free tier; GHCR has tighter GitHub integration |
| Buildpack (Cloud Native Buildpacks) | Does not support multi-arch builds natively; Dockerfile gives finer control over layer caching |
| Manual versioning (push `v*` tag to trigger release) | Adds friction; auto-patch-bump is sufficient for v1 feature work |
| Separate `release` branch | `prod` serves the same purpose; adding a third branch increases cognitive overhead |
| `push` trigger on `prod` instead of `workflow_run` | Would race with CI; could release before tests finish |
| Alpine JRE base image | ARM/v7 support is unofficial; Ubuntu has first-class ARM support |
| Single-arch image (`amd64` only) | Pi 3/4 users would run under QEMU emulation (5–10× slower) |
| Layer caching in Docker Registry (`type=registry`) | Requires GHCR authentication in CI; GHA cache (`type=gha`) is simpler and faster |
