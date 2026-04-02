# Phase 5 Implementation Plan: DevOps

## Overview

Phase 5 covers two deliverables:
1. **End-to-end (e2e) integration test** — exercises the full workflow (email ingestion → YNAB sync → classification) using WireMock to stub external APIs.
2. **GitHub Actions CI workflow** — runs all tests automatically on every push and pull request using SHA-pinned actions and least-privilege permissions.

---

## Task 1: End-to-End Test

### Approach
- `@SpringBootTest` with full application context and an in-memory SQLite database.
- `WireMockServer` started before Spring context via companion object; port injected via `@DynamicPropertySource`.
- External API base URLs made configurable via application properties (defaulting to real URLs in production).

### Changes Required

#### 1a. Make base URLs configurable
Add `@Value` injection to each infrastructure client so tests can override them:
- `YnabRestClient`: `@Value("\${app.ynab.base-url:https://api.ynab.com/v1}")`
- `FastMailJmapClient`: `@Value("\${app.fastmail.base-url:https://api.fastmail.com}")`
- `GeminiProvider`: `@Value("\${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")`

#### 1b. Add WireMock dependency
```kotlin
testImplementation("org.wiremock:wiremock:3.10.0")
```

#### 1c. Create `FullWorkflowE2eTest.kt`
Location: `src/test/kotlin/com/ynabauto/e2e/FullWorkflowE2eTest.kt`

Scenario tested:
1. DB seeded with API credentials and a category rule.
2. WireMock stubs FastMail JMAP session + Email/query + Email/get.
3. `emailIngestionService.ingest()` is called → Amazon order saved as `PENDING`.
4. WireMock stubs YNAB transactions (matching amount/date) + Gemini classify + YNAB update.
5. `ynabSyncService.sync()` is called → order transitions to `COMPLETED` with correct category.

---

## Task 2: GitHub Actions CI Workflow

### File: `.github/workflows/ci.yml`

### Security Checklist
- [x] Actions pinned to full commit SHA with version comments
- [x] Default `permissions: contents: read` (least privilege)
- [x] No secrets logged or exposed
- [x] Concurrency control to cancel outdated PR builds
- [x] Gradle cache for fast builds

### Workflow Triggers
- `push` (all branches)
- `pull_request` (all branches)

### Actions Used (SHA-pinned)
- `actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1`
- `actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9 # v4.8.0`

### Steps
1. Checkout code
2. Set up Java 17 (Temurin) with Gradle cache
3. Run `./gradlew test`
