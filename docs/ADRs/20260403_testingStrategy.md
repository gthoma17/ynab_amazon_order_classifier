# ADR-0002: Testing Strategy (WireMock + Playwright + E2EServer)

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #10 — Phase 5: CI, E2E tests (backend + Playwright), and email parser real-format fixes

---

## Context

The pipeline integrates with three third-party HTTP services (FastMail JMAP, YNAB, Gemini). Test isolation is critical for deterministic, fast, offline builds.

---

## Decision

### Backend E2E: WireMock Unified Stub

Single `WireMockServer` stubs all three external APIs. Spring Boot test uses `@DynamicPropertySource` to inject WireMock base URL before context starts:

```kotlin
companion object {
    private val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort()).also { it.start() }

    @JvmStatic
    @DynamicPropertySource
    fun configureProperties(registry: DynamicPropertyRegistry) {
        val port = wireMock.port()
        registry.add("app.ynab.base-url") { "http://localhost:$port" }
        registry.add("app.fastmail.base-url") { "http://localhost:$port" }
        registry.add("app.gemini.base-url") { "http://localhost:$port/v1beta" }
    }
}
```

**All infrastructure clients accept configurable base URLs** via `@Value`-injected parameters with production defaults. Kotlin default parameters preserve backward compatibility with unit tests.

**`@SpringBootTest` with `WebEnvironment.NONE`** — no embedded web server. Test directly invokes services. Database is in-memory SQLite.

### Frontend E2E: Playwright + E2EServer

Playwright drives Chromium against Vite dev server (5173) proxying `/api` to real Spring Boot backend (8080) with WireMock stubs.

**`E2EServer.kt`** — standalone Kotlin `main()` that starts WireMock + Spring Boot with overridden base URLs and in-memory SQLite. Gradle task `runE2EServer`. Playwright `webServer` config auto-starts both Vite and E2E backend.

**`expect.poll()` + `page.reload()`** to wait for background scheduled jobs, avoiding sleep-based timing.

---

## Consequences

- Both backend and frontend E2E tests must pass in CI before merge
- Adding new external API requires: configurable base URL, WireMock stub, `@DynamicPropertySource` update
- Scheduled jobs tested via direct service invocation (backend E2E) and side-effect polling (Playwright)
- E2E backend uses modified polling intervals (3s vs 5h) for test observability

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| MockMvc for E2E | Does not exercise full Spring context, scheduled jobs, or JPA transactions |
| Testcontainers for live APIs | Requires live credentials; slow, flaky, rate-limited |
| Separate WireMock per API | Increases port contention; single server is simpler |
| `Thread.sleep()` for scheduled jobs | Brittle; Playwright `expect.poll()` is idiomatic |
