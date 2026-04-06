# Copilot Instructions for budget-sortbot

## Project Overview

Budget Sortbot automatically classifies Amazon orders into YNAB budget categories. The pipeline is:

1. **Email ingestion** — FastMail JMAP API fetches Amazon order confirmation emails.
2. **YNAB matching** — Matches parsed orders to YNAB transactions by amount (within 10 milliunits) and date (±3 days).
3. **AI classification** — Google Gemini (`gemini-2.5-flash-lite`) picks the best YNAB budget category from user-defined rules.
4. **YNAB write** — Updates the matched YNAB transaction with the chosen category and a memo.

Everything runs inside a single Docker container. Configuration and state live in a SQLite database at `/app/data/database.sqlite`. Application logs are written to a separate Blacklite SQLite file at `/app/data/logs.sqlite`.

**Non-functional requirement — memory:** The JVM heap is capped at **512 MB** (`-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`). This is a critical NFR that enables the app to run on resource-constrained hardware such as Raspberry Pi 3 or spare NAS systems; it is a key adoption driver. Do not introduce features or dependencies that require more heap than this limit allows.

---

## Repository Layout

```
build.gradle.kts                 Gradle build (Kotlin, Spring Boot 3.3, Java 17)
settings.gradle.kts
Dockerfile                       3-stage multi-arch build (frontend → backend JAR → runtime)
frontend/                        React 19 + TypeScript + Vite + React Router v7
  src/views/                     UI pages: ConfigView, CategoryRulesView, LogsView,
                                           PendingOrdersView, GetHelpView
  src/api.ts                     All fetch calls to the backend
  src/__tests__/                 Vitest + Testing Library unit tests
  e2e/                           Playwright E2E tests
  vite.config.ts                 Build outputs to ../src/main/resources/static
src/main/kotlin/com/budgetsortbot/
  Application.kt                 Spring Boot entry point
  config/                        Spring configuration beans
  domain/                        JPA entities / enums
  infrastructure/
    ai/                          GeminiProvider (ClassificationProvider interface)
    email/                       FastMailJmapClient (EmailProviderClient interface)
    persistence/                 Spring Data JPA repositories
    ynab/                        YnabRestClient (YnabClient interface)
  service/                       Business logic
  web/                           REST controllers + DTOs
src/main/resources/
  application.yml                Runtime config (SQLite paths, Flyway)
  db/migration/                  Flyway SQL migrations (V1, V2)
src/test/kotlin/com/budgetsortbot/
  e2e/E2EServer.kt               WireMock-backed Spring Boot server for Playwright
  ...                            Unit tests mirroring the main source tree
src/test/resources/application.yml  In-memory SQLite for tests
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9, JVM 17 |
| Framework | Spring Boot 3.3 (Spring MVC, Spring Data JPA) |
| Database | SQLite via `org.xerial:sqlite-jdbc`, Hibernate community dialects |
| Migrations | Flyway (classpath:db/migration) |
| Logging | SLF4J + Logback + Blacklite (logs to a second SQLite file) |
| HTTP client | Spring `RestClient` |
| Frontend | React 19, TypeScript, Vite 8, React Router v7 |
| Frontend tests | Vitest, @testing-library/react, msw |
| E2E tests | Playwright (Chromium), WireMock for external stubs |
| Backend tests | JUnit 5, MockK, springmockk, WireMock |
| Build | Gradle (Kotlin DSL), Node 20 / npm |
| Deployment | Docker multi-arch (amd64, arm64, arm/v7) |

---

## Base Package

All Kotlin source is under `com.budgetsortbot`. The Gradle group is `com.budgetsortbot`. Do **not** use the old `com.ynabauto` package that appears in some stored memories — it has been fully renamed.

---

## Build & Test Commands

### Backend

```bash
# Run all backend unit tests
./gradlew test

# Build the production JAR (skips tests)
./gradlew bootJar -x test

# Start the WireMock-backed server for Playwright E2E tests (keeps running)
./gradlew runE2EServer
```

### Frontend (run from the `frontend/` directory)

```bash
npm ci                   # install dependencies
npm test                 # Vitest unit tests (single run)
npm run test:watch       # Vitest watch mode
npm run build            # TypeScript check + Vite build → ../src/main/resources/static/
npm run lint             # ESLint
npm run test:e2e         # Playwright E2E (starts Vite dev server and ./gradlew runE2EServer automatically via webServer config; run runE2EServer manually only when debugging outside Playwright)
```

### Docker (production build)

```bash
docker build -t budget-sortbot .
docker run -d -p 8080:8080 -v /opt/budget-sortbot/data:/app/data budget-sortbot
```

---

## Testing Conventions

### Backend

- Use **MockK** (`io.mockk:mockk`) for all mocking. Never use Mockito.
- In `@WebMvcTest` slices use **`@MockkBean`** (from `com.ninja-squad:springmockk`), not `@MockBean`.
- Service-layer tests instantiate the class under test directly and inject MockK mocks in a `@BeforeEach` setup method — no Spring context is needed.
- Infrastructure-layer tests (clients) use WireMock (`org.wiremock:wiremock-standalone`) to stub HTTP endpoints.
- Test database is in-memory SQLite: `jdbc:sqlite:file::memory:?cache=shared` (defined in `src/test/resources/application.yml`).
- `FlywayMigrationTest` verifies migrations run cleanly against the in-memory DB.

### Frontend

- Tests live in `src/__tests__/` and use Vitest globals (`describe`, `it`, `expect`) — no explicit import needed.
- Use `@testing-library/react` (`render`, `screen`, `userEvent`) with `msw` for API mocking.
- TypeScript `noUnusedLocals` and `noUnusedParameters` are enabled — unused variables are compile errors.

### E2E

- Playwright tests in `frontend/e2e/` drive Chromium against `http://localhost:5173` (Vite dev server).
- The Vite dev server proxies `/api` to `localhost:8080`.
- Run `npm run test:e2e` from `frontend/`; Playwright starts both the Vite dev server and `./gradlew runE2EServer` automatically via its `webServer` configuration (`reuseExistingServer: true` outside CI).
- Start `./gradlew runE2EServer` manually only when you need to debug the E2E backend outside the normal Playwright test flow.
- `E2EServer.kt` stubs FastMail JMAP, YNAB API, and Gemini with WireMock.

---

## Key Domain Concepts

### Order lifecycle (`OrderStatus`)

Happy path: `PENDING` → `MATCHED` → `COMPLETED`

An order is saved as `PENDING` during email ingestion. `YnabSyncService` sets it to `MATCHED` once a YNAB transaction is found, then `COMPLETED` after the category update is written to YNAB. The enum also declares `DISCARDED`, but this value is not referenced anywhere in the current codebase — it is reserved for future use.

### Sync pipeline

`SyncScheduler` runs `EmailIngestionService.ingest()` followed by `YnabSyncService.sync()` on a configurable cron schedule. Dynamic scheduling is implemented with `ThreadPoolTaskScheduler` (pool size = 1); call `SyncScheduler.reschedule()` after changing the schedule in the database.

### Schedule types (`ScheduleType`)

`HOURLY`, `EVERY_N_HOURS` (default: every 5 h), `EVERY_N_MINUTES`, `EVERY_N_SECONDS` (dev/test only), `DAILY`, `WEEKLY`. The schedule is stored as JSON under key `SCHEDULE_CONFIG` in `app_config`.

### Configuration storage

All runtime config lives in the `app_config` table (key/value). Keys are defined as constants on `ConfigService`: `YNAB_TOKEN`, `YNAB_BUDGET_ID`, `FASTMAIL_API_TOKEN`, `GEMINI_KEY`, `ORDER_CAP`, `SCHEDULE_CONFIG`, `START_FROM_DATE`, `INSTALLED_AT`.

### Dry run

`DryRunService` executes the full pipeline (email fetch → YNAB match → Gemini classify) without writing anything to YNAB. Results are stored in `dry_run_results`. Triggered via `POST /api/config/dry-run`.

### YNAB matching

`MatchingStrategy.match()` matches an `AmazonOrder` to a `YnabTransaction` by:
- Amount: `abs(ynab.amount)` within 10 milliunits of `order.totalAmount * 1000`
- Date: within ±3 days

### Logging

Application logs are written to **two sinks** configured in `logback-spring.xml`:

1. **Console (stdout)** — preserves `docker logs` behaviour, which is a critical non-functional requirement. The console appender is always active so operators can inspect live output without mounting additional volumes.
2. **Blacklite (SQLite)** — an asynchronous appender that writes to a separate SQLite file (`app.blacklite.path`, default `./data/logs.sqlite`). `ApplicationLogService` queries this file via a dedicated `JdbcTemplate` (`logsDataSource`), powering the in-UI log viewer.

Because adding any `DataSource` bean causes Spring Boot's auto-configuration to back off, a `@Primary` bean is declared in `PrimaryDataSourceConfig`.

---

## REST API Summary

All endpoints are under `/api/`.

| Method | Path | Description |
|---|---|---|
| GET | `/api/config/keys` | Get stored API keys |
| PUT | `/api/config/keys` | Update API keys |
| GET | `/api/config/categories` | Get category rules |
| PUT | `/api/config/categories` | Replace category rules |
| GET | `/api/config/processing` | Get order cap, start date, schedule |
| PUT | `/api/config/processing` | Update processing settings + reschedule |
| POST | `/api/config/probe/fastmail` | Test FastMail connection |
| POST | `/api/config/probe/ynab` | Test YNAB connection |
| POST | `/api/config/probe/gemini` | Test Gemini connection |
| POST | `/api/config/dry-run` | Trigger a dry run |
| GET | `/api/config/dry-run/results` | Fetch dry run results |
| GET | `/api/ynab/budgets` | List YNAB budgets for a token |
| GET | `/api/ynab/categories` | List YNAB categories for a budget |
| GET | `/api/orders/pending` | List pending Amazon orders |
| GET | `/api/logs` | List recent sync logs |
| POST | `/api/help/report` | Build a GitHub issue body from logs |

SPA routing: `WebConfig` forwards all single-segment non-file paths to `index.html` using the pattern `/{path:[^\\.]*}`. The Spring Boot 6 `PathPatternParser` does **not** accept `/**/{path}` patterns.

---

## External Service Integration

### FastMail JMAP

`FastMailJmapClient` implements `EmailProviderClient`. It calls the JMAP session discovery endpoint (`/.well-known/jmap`), then uses `Email/query` + `Email/get` to find Amazon order emails. Base URL is configurable via `app.fastmail.base-url` (overridden in E2E tests).

### YNAB API

`YnabRestClient` implements `YnabClient`. Calls `GET /v1/budgets`, `GET /v1/budgets/{id}/categories`, `GET /v1/budgets/{id}/transactions`, `PUT /v1/budgets/{id}/transactions/{id}`. Base URL: `app.ynab.base-url`.

### Gemini AI

`GeminiProvider` implements `ClassificationProvider`. Uses model `gemini-2.5-flash-lite`. Sends a zero-shot prompt listing order items and available category rules; expects the response to be exactly one category ID. Base URL: `app.gemini.base-url`.

---

## Database Migrations

Flyway migrations are in `src/main/resources/db/migration/`:

- `V1__init.sql` — creates `app_config`, `category_rules`, `amazon_orders`, `sync_logs`
- `V2__safety_controls.sql` — seeds `INSTALLED_AT`, `START_FROM_DATE`, `ORDER_CAP`, `SCHEDULE_CONFIG` defaults; creates `dry_run_results`

**Always add a new versioned migration file** when changing the schema. Do not modify existing migration files.

---

## Frontend Architecture

- React Router v7 with client-side routing; the Spring SPA fallback serves `index.html` for all UI routes.
- `src/api.ts` centralises all `fetch` calls.
- Views: `ConfigView` (keys + connection probes + schedule + dry run), `CategoryRulesView`, `LogsView`, `PendingOrdersView`, `GetHelpView`.
- Vite `build.outDir` is `../src/main/resources/static` (`emptyOutDir: true`). Built assets are gitignored; they are produced during the Docker build by the `frontend-build` stage.

---

## Common Pitfalls

1. **DataSource auto-config** — adding any new `DataSource` bean will cause Spring Boot to back off from all auto-configuration. Always ensure a `@Primary DataSource` is explicitly declared (see `PrimaryDataSourceConfig`).
2. **SPA routing pattern** — use `/{path:[^\\.]*}` not `/**/{path}` for single-level SPA forwarding under Spring Boot 3 / Spring 6.
3. **SQLite pool size** — keep `maximumPoolSize = 1` for both data sources; SQLite serialises writes and a larger pool causes contention.
4. **Schedule reschedule** — after saving a new `SCHEDULE_CONFIG` value, always call `syncScheduler.reschedule()` so the change takes effect without a restart.
5. **`encodeURIComponent` parity** — `HelpController` contains a custom Kotlin implementation that exactly matches JS `encodeURIComponent` (RFC 3986 unreserved + `!~*'()`) for accurate GitHub URL length budgeting.
6. **`noUnusedLocals`/`noUnusedParameters`** — the TypeScript config treats unused variables as errors; do not introduce dead code in the frontend.
7. **Test mocking** — always use `@MockkBean` (springmockk) in Spring MVC test slices, not `@MockBean`.
8. **512 MB heap constraint** — the `JAVA_TOOL_OPTIONS` in the `Dockerfile` sets `-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`. This must not be exceeded; it enables deployment on Raspberry Pi 3 / NAS hardware and is a critical adoption NFR.
