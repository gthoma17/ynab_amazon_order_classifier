# ADR-0004: Logging Architecture (Blacklite SQLite + Dual DataSource + Console Preservation)

**Date:** 2026-04-05
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #43 — Enable Blacklite SQLite log sink and surface application logs on Get Help page

---

## Context

The Get Help page (PR #23) generated sanitized bug reports from `sync_logs` (business-level audit trail) but had no access to application logs (DEBUG/INFO/WARN/ERROR from Logback). Users encountering framework errors or startup failures had to `docker logs` the container and manually copy-paste output into GitHub issues.

Early attempts to add a Blacklite SQLite appender (in-process log storage) caused `SQLITE_BUSY` errors on fresh startup: Blacklite opened `database.sqlite` during JVM init (before Spring), while Flyway (running inside the Spring context) tried to write migrations to the same file. SQLite's write lock prevented Flyway's baseline commit from completing, crashing every fresh install.

---

## Decision

### Separate SQLite Files for Data vs. Logs

**Primary datasource** — `./data/database.sqlite` (default via `spring.datasource.url`)
**Log datasource** — `./data/logs.sqlite` (default via `app.blacklite.path`)

Both files live under the same Docker volume mount (`/app/data`), but the split eliminates write-lock contention. Flyway only touches the primary datasource; Blacklite only touches the log datasource.

### Dual DataSource Configuration

Spring Boot's DataSource auto-configuration backs off when *any* `DataSource` bean is manually defined. To preserve Flyway auto-config and JPA, we explicitly declare:

1. **`@Primary DataSource`** (`PrimaryDataSourceConfig`) — wired to `spring.datasource.*` properties; used by Flyway and JPA
2. **`logsDataSource`** (`LogDataSourceConfig`) — wired to `app.blacklite.path`; used only by `ApplicationLogService`

Both use HikariCP with `maximumPoolSize = 1` (SQLite write serialisation).

### Application Log Service (No JPA)

`ApplicationLogService` queries the `entries` table via `JdbcTemplate(logsDataSource)`. The JPA entity approach (`BlackliteEntry`, `BlackliteEntryRepository`) was removed — JPA cannot map tables from a non-`@Primary` datasource without complex `@EntityManager` / `@Transactional` qualifier wiring that is not worth the abstraction cost for a single SELECT query.

### Console Output Preserved (Critical NFR)

**`docker logs` output is a critical non-functional requirement.** Operators debugging startup failures or runtime errors expect to see logs via `docker logs <container>` without mounting a volume or installing `sqlite3`. The Logback configuration writes to **two sinks**:

1. **Console (stdout)** — always active, preserves `docker logs` behaviour
2. **Blacklite (async)** — writes to `./data/logs.sqlite` asynchronously; failures do not crash the app

Both sinks receive the same log events. Console output is unaffected by Blacklite's presence.

### Selective Redaction in Help Reports

`ReportSanitizationService` redacts API credentials from help reports but **does not redact** `ORDER_CAP`, `SCHEDULE_CONFIG`, `START_FROM_DATE`, `INSTALLED_AT` — these are configuration metadata, not secrets. All other `app_config` keys are redacted by default.

Redaction runs server-side before the response leaves the JVM. The frontend never sees raw sensitive values.

---

## Rationale

**Why separate SQLite files instead of solving `SQLITE_BUSY` with WAL mode or timeouts?**
- WAL mode creates `-wal` and `-shm` sidecar files; Spring Boot / Flyway do not support WAL pragma injection before the first connection
- Increasing the busy timeout (`PRAGMA busy_timeout`) mitigates but does not eliminate the race — Blacklite opens the file during class loading, before Spring can inject timeouts
- Separate files eliminate the race entirely; the logging DB has no migrations, so Blacklite's early open is not a problem

**Why HikariCP for the log datasource instead of a single `DriverManager` connection?**
- Consistency with the primary datasource (both use HikariCP)
- HikariCP handles connection lifecycle and validation; direct `DriverManager` connections would require manual `try-finally` cleanup in `ApplicationLogService`
- Pool size = 1 so there is no resource overhead vs. a raw connection

**Why JdbcTemplate instead of JPA for log queries?**
- The `entries` table lives in a separate database; JPA's `@EntityManager` is tied to the `@Primary` datasource
- Qualifying `@PersistenceContext(unitName = "logs")` requires a second `LocalContainerEntityManagerFactoryBean` and `@EnableJpaRepositories(entityManagerFactoryRef = ...)` — heavyweight for a single query
- `JdbcTemplate` is a 10-line query; JPA abstraction provides no value here

**Why async Blacklite appender instead of sync?**
- Log writes are I/O-bound; async prevents application threads from blocking on SQLite writes
- `AsyncAppender` has a bounded queue (default 256); if the queue fills, new log events are dropped (not a failure case — the app continues running)
- Sync appender would add SQLite latency to every log statement in the hot path (email parsing, YNAB sync)

**Why preserve console output instead of logging to SQLite only?**
- `docker logs` is a critical operator tool for debugging startup failures, OOM kills, and framework errors
- SQLite-only logging would require operators to `docker exec` into the container, install `sqlite3`, and query the DB — unacceptable UX for first-time installers
- Console + SQLite gives operators two paths: `docker logs` for live tailing, Get Help page for structured historical queries

**Why redact some config keys but not others?**
- `ORDER_CAP`, `SCHEDULE_CONFIG`, `START_FROM_DATE`, `INSTALLED_AT` are configuration metadata that help diagnose issues (e.g., "why did 100 orders process at once?" → order cap is 0)
- API tokens (`YNAB_TOKEN`, `FASTMAIL_API_TOKEN`, `GEMINI_KEY`) are credentials that could be abused if leaked in a GitHub issue

---

## Consequences

- `./data/logs.sqlite` is a new file created on first startup; existing Docker volume mounts continue to work (both `database.sqlite` and `logs.sqlite` are under `/app/data`)
- Adding any new `DataSource` bean in the future requires checking that `PrimaryDataSourceConfig` is still `@Primary` — Spring Boot will silently back off from auto-config otherwise
- Get Help page reports now include application logs (DEBUG/INFO/WARN/ERROR) with API keys redacted; users no longer need to manually copy-paste `docker logs` output
- `docker logs <container>` continues to work unchanged; operators can tail live output without mounting the logs volume
- Blacklite appender failures (e.g., disk full, corrupted SQLite file) do not crash the application — logs fall back to console-only
- Any story that queries application logs must use `logsDataSource` (via `ApplicationLogService`), not the `@Primary` datasource

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Single SQLite file with WAL mode | Requires pragma injection before Flyway runs; Spring Boot does not support per-datasource init scripts cleanly |
| Single SQLite file with retry logic | Mitigates but does not eliminate `SQLITE_BUSY`; retry delays increase startup time |
| PostgreSQL / MySQL for logs | Requires a separate server process; violates the Pi 3 single-container constraint |
| Log to filesystem (`logs/app.log`) | Requires volume mount for log access; Get Help page would need to parse unstructured text files; SQLite gives structured queries for free |
| ELK / Loki / CloudWatch | Out of scope for v1; requires external infrastructure and network egress |
| Spring Boot Admin for log access | Requires a separate Admin server; overkill for a single-tenant app |
| JPA for log queries with `@PersistenceContext(unitName = "logs")` | Requires a second `EntityManagerFactory` and `@EnableJpaRepositories` qualifier; heavyweight for a single query |
| Sync Blacklite appender | Adds SQLite I/O latency to application threads; async appender is the Logback best practice for any I/O-bound sink |
