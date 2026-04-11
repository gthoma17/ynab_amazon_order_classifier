# ADR-0004: Logging Architecture (Dual SQLite + Console Preservation)

**Date:** 2026-04-05
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #43 — Enable Blacklite SQLite log sink and surface application logs on Get Help page

---

## Context

Get Help page needed application logs (DEBUG/INFO/WARN/ERROR), not just business-level `sync_logs`. Early attempt to add Blacklite SQLite appender caused `SQLITE_BUSY` on startup: Blacklite opened `database.sqlite` during JVM init (before Spring), while Flyway (running inside Spring) tried to write migrations to the same file.

---

## Decision

### Separate SQLite Files

**Primary datasource:** `./data/database.sqlite`  
**Log datasource:** `./data/logs.sqlite`

Both under same Docker volume mount, but split eliminates write-lock contention.

### Dual DataSource Configuration

Spring Boot DataSource auto-config backs off when *any* `DataSource` bean is defined. Explicitly declare:

1. **`@Primary DataSource`** — wired to `spring.datasource.*`; used by Flyway and JPA
2. **`logsDataSource`** — wired to `app.blacklite.path`; used only by `ApplicationLogService`

Both use HikariCP with `maximumPoolSize = 1` (SQLite write serialisation).

### Application Log Queries via JdbcTemplate

`ApplicationLogService` queries `entries` table via `JdbcTemplate(logsDataSource)`. JPA cannot map tables from non-`@Primary` datasource without complex `@EntityManager` wiring. JdbcTemplate is a 10-line query; JPA abstraction provides no value.

### Console Output Preserved (Critical NFR)

**`docker logs` is a critical non-functional requirement.** Logback writes to **two sinks**:

1. **Console (stdout)** — always active
2. **Blacklite (async)** — writes to logs.sqlite; failures do not crash the app

Both receive the same log events. Operators can `docker logs` without mounting volumes or installing `sqlite3`.

---

## Consequences

- `./data/logs.sqlite` created on first startup; existing volume mounts continue to work
- Adding new `DataSource` beans requires checking `@Primary` is set
- Get Help reports include application logs with credentials redacted
- `docker logs` continues to work unchanged
- Blacklite failures (disk full, corrupted file) do not crash the app

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Single SQLite file with WAL mode | Spring Boot does not support per-datasource init scripts cleanly |
| PostgreSQL / MySQL for logs | Requires separate server; violates Pi 3 single-container constraint |
| Log to filesystem (`logs/app.log`) | Get Help page would need to parse unstructured text; SQLite gives structured queries |
| JPA for log queries | Requires second `EntityManagerFactory`; heavyweight for a single query |
