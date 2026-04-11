# Architecture Decisions

This document is a quick-reference summary of the Architecture Decision Records (ADRs) for Budget Sortbot.
**Read this before starting any story.** Follow links to the full ADR for deeper context on any decision.

---

## How to Use This Document

- Scan the table below before beginning a new task to check whether your planned approach conflicts with a prior decision.
- Follow the link in the **ADR** column to read the full rationale for decisions that are relevant to your story.
- If your story introduces a new architectural decision, create a new ADR file under `docs/ADRs/` using the filename pattern `YYYYMMDD_shortCamelCaseTitle.md` and add a summary row to this table.

---

## How to Write Good ADRs

ADRs document **architecture decisions** (how the system is built), not product decisions (what features exist). Follow these principles.

### Focus on Architecture, Not Product

- **Architecture (HOW)**: Implementation patterns, technical tradeoffs, infrastructure choices
- **Product (WHAT)**: Features, user flows, business requirements

**Example**: ADR-0003 documents *how* dynamic scheduling is implemented (`ThreadPoolTaskScheduler` vs. `@Scheduled`), not *that* users can configure schedules.

### Keep It Concise

**Target**: Sum of `ARCHITECTURE_DECISIONS.md` + any given ADR < 10,000 characters

- Write for developers who need to understand tradeoffs, not exhaustive tutorials
- Each ADR should answer: "Why this approach instead of alternatives?"
- If you find yourself writing pages, you're documenting the code, not the decision

### Reference Code, Don't Copy It

**Don't paste code blocks that will become stale.** Instead, reference source files:

- ❌ "Here's the 50-line `SyncScheduler` implementation..."
- ✅ "See `SyncScheduler.kt` — uses `ThreadPoolTaskScheduler` with pool size = 1"

### Essential Structure

Every ADR must include:

1. **Context**: What problem existed? What constraints mattered?
2. **Decision**: What approach was chosen? Key implementation details?
3. **Consequences**: What does this force future developers to do (or avoid)?
4. **Alternatives Not Chosen**: What was rejected and why?

### Avoid Over-Detailed Configuration Examples

Configuration lives in code. ADRs explain *why* a config structure was chosen, not the full schema.

- ❌ 100-line GitHub Actions workflow YAML
- ✅ "Uses `workflow_run` trigger to gate releases on CI success"

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
| 12 | Testing strategy (WireMock + Playwright) | Backend E2E uses WireMock unified stub. Frontend E2E uses Playwright against real stack via `E2EServer`. Dynamic base URLs via `@DynamicPropertySource`. | [ADR-0002](docs/ADRs/20260403_testingStrategy.md) |
| 13 | Dynamic scheduling with SyncScheduler | Replace `@Scheduled` with `ThreadPoolTaskScheduler` reading cron from DB. Runtime reschedule without restart. Pool size = 1 for Pi 3. | [ADR-0003](docs/ADRs/20260403_safetyControls.md) |
| 14 | Logging (Blacklite + dual datasource) | Separate SQLite files for data vs. logs to avoid `SQLITE_BUSY`. Dual `DataSource` with `@Primary`. Console preserved for `docker logs`. | [ADR-0004](docs/ADRs/20260405_loggingArchitecture.md) |
| 15 | Code hygiene (hooks + CI) | Spotless/ktlint, Prettier/ESLint, Lefthook pre-commit/pre-push hooks. CI gates on format/lint. | [ADR-0005](docs/ADRs/20260406_codeHygieneTooling.md) |
| 16 | Release pipeline (multi-arch GHCR) | `workflow_run` on CI success. Auto-patch-bump. Multi-arch Docker with `--platform=$BUILDPLATFORM` for build stages. | [ADR-0006](docs/ADRs/20260403_releasePipeline.md) |
| 17 | Help/issue reporting (security + data control) | Server-side credential sanitization. GitHub URL with query params (not API) for user data control. Truncation at 4000 chars. | [ADR-0007](docs/ADRs/20260403_helpIssueReporting.md) |
| 18 | Skeuomorphic Industrial Control design system | All UI must follow the Skeuomorphic Industrial Control visual language documented in `UI_DESIGN_PRINCIPLES.md`. | [ADR-0008](docs/ADRs/20260408_skeuomorphicIndustrialControlUiDesignSystem.md#1-persistent-panel-pattern) |
| 19 | Persistent panel pattern | UI panels always occupy a fixed housing; state changes drive content inside a panel, not the panel's existence. | [ADR-0008](docs/ADRs/20260408_skeuomorphicIndustrialControlUiDesignSystem.md#1-persistent-panel-pattern) |
| 20 | SplitFlapSlot for status messages | Transient save confirmations and warnings use `SplitFlapSlot` (5 s timeout) not conditionally rendered text. | [ADR-0008](docs/ADRs/20260408_skeuomorphicIndustrialControlUiDesignSystem.md#2-split-flap-slot-as-saveStatus-indicator) |
| 21 | RadioGroup for static option selection; CRT for dynamic | Use `RadioGroup` (indicator radio group) when all options are known ahead of time. Use a CRT terminal panel when options are retrieved dynamically (e.g., YNAB budget list). The budget selector CRT also serves as implicit YNAB token validation — no separate Test YNAB button is needed. | [ADR-0008](docs/ADRs/20260408_skeuomorphicIndustrialControlUiDesignSystem.md#3-indicator-radio-group-for-mode-selection) |
| 22 | Self-hosted fonts, no external CDN | External font CDNs (Google Fonts) are prohibited; use system monospace fallbacks to preserve offline/privacy guarantees. | [ADR-0008](docs/ADRs/20260408_skeuomorphicIndustrialControlUiDesignSystem.md#6-self-hosted--system-fonts-only--no-external-cdn) |

---

## All ADRs

| File | Title | Date | Status |
|---|---|---|---|
| [20260406_foundationalArchitecture.md](docs/ADRs/20260406_foundationalArchitecture.md) | Foundational Architecture Decisions | 2026-04-06 | Accepted |
| [20260403_testingStrategy.md](docs/ADRs/20260403_testingStrategy.md) | Testing Strategy (WireMock + Playwright + E2EServer Pattern) | 2026-04-03 | Accepted |
| [20260403_safetyControls.md](docs/ADRs/20260403_safetyControls.md) | Dynamic Scheduling with SyncScheduler | 2026-04-03 | Accepted |
| [20260405_loggingArchitecture.md](docs/ADRs/20260405_loggingArchitecture.md) | Logging Architecture (Blacklite SQLite + Dual DataSource + Console Preservation) | 2026-04-05 | Accepted |
| [20260406_codeHygieneTooling.md](docs/ADRs/20260406_codeHygieneTooling.md) | Code Hygiene Tooling (Pre-Commit Hooks + CI Enforcement) | 2026-04-06 | Accepted |
| [20260403_releasePipeline.md](docs/ADRs/20260403_releasePipeline.md) | Automated Release Pipeline (Multi-Arch Docker to GHCR) | 2026-04-03 | Accepted |
| [20260403_helpIssueReporting.md](docs/ADRs/20260403_helpIssueReporting.md) | Help/Issue Reporting Security and Data Control | 2026-04-03 | Accepted |
| [20260408_skeuomorphicIndustrialControlUiDesignSystem.md](docs/ADRs/20260408_skeuomorphicIndustrialControlUiDesignSystem.md) | Skeuomorphic Industrial Control UI Design System | 2026-04-08 | Accepted |
