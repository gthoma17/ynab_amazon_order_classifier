# Phase 5 Implementation Plan: DevOps

## Goals
1. **E2E Test**: Full workflow test using WireMock to mock FastMail, YNAB, and Gemini external APIs.
2. **GitHub Actions CI**: Run tests automatically on every push.

## Steps

### 1. Make Infrastructure Base URLs Configurable
- `YnabRestClient`: add `@Value("\${app.ynab.base-url:https://api.ynab.com/v1}")` constructor param
- `FastMailJmapClient`: add `@Value("\${app.fastmail.base-url:https://api.fastmail.com}")` constructor param
- `GeminiProvider`: add `@Value("\${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")` constructor param
- Kotlin defaults preserve backward compatibility with existing unit tests

### 2. Add WireMock Dependency
- Add `testImplementation("org.wiremock:wiremock-standalone:3.13.2")` to build.gradle.kts

### 3. Create E2E Test (`FullWorkflowE2ETest.kt`)
- `@SpringBootTest(webEnvironment = NONE)` – full context, no HTTP server
- Static WireMock server started in companion object; port injected via `@DynamicPropertySource`
- `@BeforeEach`: reset WireMock, clear orders/rules, seed credentials + one category rule
- Stubs: FastMail JMAP session, Email/query, Email/get; YNAB transactions list + update; Gemini classification
- Test: call `emailIngestionService.ingest()` → assert PENDING order; call `ynabSyncService.sync()` → assert COMPLETED order with correct category + YNAB update call

### 4. Create GitHub Actions CI (`.github/workflows/ci.yml`)
- Trigger on push (all branches) and pull_request
- Concurrency: cancel in-progress runs on PRs
- `permissions: contents: read`
- Actions pinned to full commit SHAs with version comments
- `actions/checkout`, `actions/setup-java` (Java 17 Temurin), `gradle/actions/setup-gradle` (handles caching + wrapper validation)
- Run `./gradlew test`

### 5. Delete PHASE_5_PLAN.md before final commit
