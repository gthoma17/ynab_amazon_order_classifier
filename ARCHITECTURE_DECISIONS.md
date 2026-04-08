# Architecture Decisions

This document is a quick-reference summary of the Architecture Decision Records (ADRs) for Budget Sortbot.
**Read this before starting any story.** Follow links to the full ADR for deeper context on any decision.

---

## How to Use This Document

- Scan the table below before beginning a new task to check whether your planned approach conflicts with a prior decision.
- Follow the link in the **ADR** column to read the full rationale for decisions that are relevant to your story.
- If your story introduces a new architectural decision, create a new ADR file under `docs/ADRs/` using the filename pattern `YYYYMMDD_shortCamelCaseTitle.md` and add a summary row to this table.

---

## Decision Summary

| # | Decision | Summary | ADR |
|---|---|---|---|
| 1 | JVM / Kotlin / Spring Boot 3 | Backend runs on JVM 17 with Kotlin 1.9 and Spring Boot 3.3. Chosen for ecosystem familiarity and opinionated auto-configuration. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#1-runtime--jvm--kotlin-on-spring-boot-3) |
| 2 | 512 MB heap cap, Serial GC | `-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m` — non-negotiable for Raspberry Pi 3 deployment. Do not exceed. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#2-memory-constraint--512-mb-jvm-heap-serial-gc) |
| 3 | Layered architecture (not hexagonal) | Controller → Service → Infrastructure. Hexagonal ports/adapters explicitly rejected to reduce abstraction overhead. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#3-architecture-style--layered-controller--service--infrastructure) |
| 4 | SQLite + Flyway, pool size 1 | Single-file DB with no server process. Flyway manages migrations. Pool size must remain 1 (SQLite serialises writes). | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#4-database--sqlite-with-flyway-migrations) |
| 5 | React SPA co-hosted by Spring Boot | UI built at image-construction time and served as static files. No separate frontend server or CDN. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#5-frontend--react-spa-co-hosted-by-spring-boot) |
| 6 | Spring RestClient for all HTTP | Synchronous `RestClient` used for FastMail, YNAB, and Gemini. No WebFlux/reactive, no Feign. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#6-http-client--spring-restclient) |
| 7 | FastMail JMAP for email | Email ingestion via JMAP (`Email/query` + `Email/get`). IMAP avoided due to connection-state complexity. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#7-email-provider--fastmail-via-jmap) |
| 8 | Google Gemini for AI classification | `gemini-2.5-flash-lite` model. Zero-shot prompting. Only called after a YNAB match is found. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#8-ai-classification--google-gemini-gemini-25-flash-lite) |
| 9 | Single-tenant v1, schema ready for v2 | No `tenant_id` in v1 but tables are designed to accept it. No global static state. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#9-tenancy--single-tenant-v1-schema-structured-for-v2) |
| 10 | API keys stored plaintext in DB | Keys in `app_config` table, no encryption at rest in v1. The entire security posture assumes deployment behind a home router/DMZ; the management console is accessible only to the operator and is never internet-exposed. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#10-security--api-keys-in-db-plaintext-ui-not-internet-exposed) |
| 11 | Multi-stage Docker, multi-arch | Three-stage Dockerfile (Node → Gradle → JRE). Published for `amd64`, `arm64`, `arm/v7`. | [ADR-0001](docs/ADRs/20260406_foundationalArchitecture.md#11-build-and-deployment--multi-stage-docker-multi-arch) |
| 12 | Testing strategy (WireMock + Playwright) | Backend E2E uses WireMock unified stub for all external APIs. Frontend E2E uses Playwright against real stack via `E2EServer` (Spring Boot + WireMock). Dynamic base URLs via `@DynamicPropertySource`. | [ADR-0002](docs/ADRs/20260403_testingStrategy.md) |
| 13 | Safety controls | Order cap (default 0=unlimited), start-from date (default=install date), dynamic schedule (`SyncScheduler` replaces `@Scheduled`), dry run (preview without YNAB writes). All persisted in `app_config`. | [ADR-0003](docs/ADRs/20260403_safetyControls.md) |
| 14 | Logging architecture (Blacklite + dual datasource) | Separate SQLite files for data (`database.sqlite`) vs. logs (`logs.sqlite`) to avoid `SQLITE_BUSY`. Dual `DataSource` config with `@Primary` for main DB. Console output preserved for `docker logs`. Application logs queried via `JdbcTemplate`, not JPA. | [ADR-0004](docs/ADRs/20260405_loggingArchitecture.md) |
| 15 | Code hygiene tooling | Spotless + ktlint 1.5.0 (pinned) for Kotlin, Prettier 3.5.3 (exact-pinned) for TypeScript, ESLint for React. Lefthook pre-commit hooks (format/lint) and pre-push hooks (tests). CI gates on format/lint. | [ADR-0005](docs/ADRs/20260406_codeHygieneTooling.md) |
| 16 | Automated release pipeline | `workflow_run` on CI success on `prod` branch. Auto-patch-bump versioning (v1.0.0 → v1.0.1). Multi-arch Docker (`amd64`, `arm64`, `arm/v7`) to GHCR. BuildKit layer caching. `--platform=$BUILDPLATFORM` for build stages (QEMU only for runtime). | [ADR-0006](docs/ADRs/20260403_releasePipeline.md) |
| 17 | Help/issue reporting system | "Get Help" page generates sanitized GitHub issue pre-fill. Server-side redaction (API keys → `[REDACTED]`, metadata preserved). Truncation at 4000 chars. `window.open` to GitHub new-issue URL. | [ADR-0007](docs/ADRs/20260403_helpIssueReporting.md) |
| 18 | Configuration UX improvements | FastMail: single API token (removed username field). YNAB: budget dropdown (auto-populated from `GET /budgets`) instead of manual UUID entry. Budget ID remains UUID internally. | [ADR-0008](docs/ADRs/20260403_configurationUxImprovements.md) |

---

## All ADRs

| File | Title | Date | Status |
|---|---|---|---|
| [20260406_foundationalArchitecture.md](docs/ADRs/20260406_foundationalArchitecture.md) | Foundational Architecture Decisions | 2026-04-06 | Accepted |
| [20260403_testingStrategy.md](docs/ADRs/20260403_testingStrategy.md) | Testing Strategy (WireMock + Playwright + E2EServer Pattern) | 2026-04-03 | Accepted |
| [20260403_safetyControls.md](docs/ADRs/20260403_safetyControls.md) | Safety Controls (Order Cap, Schedule, Start-From Date, Dry Run) | 2026-04-03 | Accepted |
| [20260405_loggingArchitecture.md](docs/ADRs/20260405_loggingArchitecture.md) | Logging Architecture (Blacklite SQLite + Dual DataSource + Console Preservation) | 2026-04-05 | Accepted |
| [20260406_codeHygieneTooling.md](docs/ADRs/20260406_codeHygieneTooling.md) | Code Hygiene Tooling (Spotless, Prettier, ESLint, Lefthook Pre-Commit Hooks) | 2026-04-06 | Accepted |
| [20260403_releasePipeline.md](docs/ADRs/20260403_releasePipeline.md) | Automated Release Pipeline (Multi-Arch Docker to GHCR) | 2026-04-03 | Accepted |
| [20260403_helpIssueReporting.md](docs/ADRs/20260403_helpIssueReporting.md) | Help/Issue Reporting System (Sanitized GitHub Issue Pre-Fill) | 2026-04-03 | Accepted |
| [20260403_configurationUxImprovements.md](docs/ADRs/20260403_configurationUxImprovements.md) | Configuration UX Improvements (FastMail Single Token, YNAB Budget Dropdown) | 2026-04-03 | Accepted |
