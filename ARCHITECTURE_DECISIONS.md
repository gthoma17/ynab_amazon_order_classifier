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

---

## All ADRs

| File | Title | Date | Status |
|---|---|---|---|
| [20260406_foundationalArchitecture.md](docs/ADRs/20260406_foundationalArchitecture.md) | Foundational Architecture Decisions | 2026-04-06 | Accepted |
