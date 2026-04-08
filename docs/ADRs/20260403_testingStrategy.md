# ADR-0002: Testing Strategy (WireMock + Playwright + E2EServer Pattern)

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #10 — Phase 5: CI, E2E tests (backend + Playwright), and email parser real-format fixes

---

## Context

Phase 5 of PLAN.md required E2E testing infrastructure to verify the complete pipeline (email ingestion → YNAB matching → Gemini classification → YNAB update) without hitting live external APIs. The system integrates with three third-party HTTP services (FastMail JMAP, YNAB REST API, Google Gemini), making test isolation critical for deterministic, fast, offline builds.

---

## Decision

### Backend E2E Testing

**Use WireMock as a unified stub for all external APIs.** A single `WireMockServer` instance (started on a dynamic port) stubs all three external services. Spring Boot test configuration uses `@DynamicPropertySource` to inject the WireMock base URL before the application context starts:

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

**All infrastructure clients accept configurable base URLs.** `YnabRestClient`, `FastMailJmapClient`, and `GeminiProvider` each accept a `@Value`-injected base URL parameter with production defaults. Kotlin default parameter values preserve backward compatibility with unit tests that construct these classes directly.

**Test uses full Spring Boot context (`@SpringBootTest`) with `WebEnvironment.NONE`** — no embedded web server is started; the test directly invokes `EmailIngestionService.ingest()` and `YnabSyncService.sync()` as synchronous method calls. Database is in-memory SQLite (`jdbc:sqlite:file::memory:?cache=shared`).

### Frontend E2E Testing

**Playwright drives Chromium against the real stack.** The E2E test runs the React SPA served by a live Vite dev server (port 5173), which proxies `/api` requests to a real Spring Boot backend (port 8080) configured to call WireMock stubs instead of live external APIs.

**`E2EServer.kt` — standalone Kotlin `main()` for Playwright.** This class starts:
1. A WireMock server on a random port with stubs for FastMail JMAP, YNAB, and Gemini
2. The full Spring Boot application with `app.*.base-url` properties overridden to point at WireMock
3. In-memory SQLite databases (separate files for data and logs to avoid `SQLITE_BUSY` contention)
4. Modified polling intervals (`app.email.poll-interval-ms=3000`) to make the scheduled background sync observable within the E2E test timeout window

**Gradle task `runE2EServer`** (test classpath) runs `E2EServer.main()`. Playwright's `webServer` configuration auto-starts both the Vite dev server and the E2E backend before tests run; `reuseExistingServer: true` outside CI allows manual debugging.

**Playwright test uses `expect.poll()` + `page.reload()`** to wait for background scheduled jobs (email ingestion, YNAB sync) to complete, avoiding brittle sleep-based timing.

---

## Rationale

**Why WireMock?**
- Industry-standard HTTP stubbing library with predictable semantics
- Single server instance reduces port contention and simplifies `DynamicPropertySource` wiring
- Request verification (`wireMock.verify(...)`) validates that the correct API calls were made with the correct payloads

**Why `@DynamicPropertySource` instead of `@TestPropertySource`?**
- WireMock port is assigned at runtime (`.dynamicPort()`), not known until the server starts
- `@TestPropertySource` evaluates before `@BeforeAll` / companion object init
- `@DynamicPropertySource` runs after the companion object starts the server, allowing property values to reference the dynamic port

**Why configurable base URLs with Kotlin default parameters?**
- Production code retains hardcoded defaults (no YNAB token accidentally sent to localhost)
- Test code can override via Spring properties (`@DynamicPropertySource`)
- Unit tests that construct clients directly (no Spring context) still work without passing base URLs

**Why a standalone `E2EServer.kt` instead of `@SpringBootTest` for Playwright?**
- Playwright runs in a separate Node.js process; it cannot invoke JVM test code directly
- A `main()` function is the only way to start a Spring Boot application that Playwright's browser can connect to
- Separate SQLite files for data vs. logs prevents `SQLITE_BUSY` errors during Flyway migrations (see ADR-0004 for logging architecture)

**Why `WebEnvironment.NONE` for backend E2E?**
- The backend E2E test validates service-layer orchestration, not HTTP routing
- Controllers are covered by `@WebMvcTest` slices in their own test classes
- Skipping the embedded Tomcat server reduces test startup time and memory footprint

**Why in-memory SQLite for tests?**
- No filesystem pollution
- `?cache=shared` required for multi-connection coherence (Flyway + application both access the same in-memory DB)
- Matches production SQLite dialect without requiring a separate test-only DB (H2/Derby)

---

## Consequences

- Every PR must pass both the backend E2E test (WireMock-based `FullWorkflowE2ETest`) and the Playwright frontend E2E test (real stack via `E2EServer`) in CI before merge
- Adding a new external API integration requires:
  1. Adding `@Value`-injected base URL to the client implementation
  2. Adding a WireMock stub in `FullWorkflowE2ETest` and `E2EServer`
  3. Updating `@DynamicPropertySource` / `E2EServer.main()` to inject the base URL
- Scheduled background jobs (email ingestion, YNAB sync) are tested via direct service method invocation in backend E2E; Playwright E2E validates they run on schedule by polling for side effects
- The E2E backend (`E2EServer`) uses modified polling intervals (3 seconds instead of 5 hours) to make tests observable — this is configured via Spring properties, not test-only code paths

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| MockMvc + `@WebMvcTest` for E2E | Does not exercise the full Spring context; scheduled jobs, service orchestration, and JPA transactions are not covered |
| Testcontainers for live FastMail/YNAB/Gemini | Requires live API credentials in CI; tests would be slow, flaky, and rate-limited; WireMock stubs are deterministic and offline |
| Separate WireMock servers per external API | Increases port contention risk; `@DynamicPropertySource` would need three ports; single server is simpler |
| Embedded WireMock rule in Playwright Node.js | E2E backend is Kotlin/Spring Boot; cannot share WireMock state between JVM and Node.js; `E2EServer` keeps stubs colocated with the backend |
| `Thread.sleep()` to wait for scheduled jobs | Brittle timing; Playwright `expect.poll()` with page reload is idiomatic and robust |
| Spring Boot Actuator health checks for readiness | Overkill for a single-tenant app with no Kubernetes/liveness probes; polling for visible side effects (orders/logs) is sufficient |
