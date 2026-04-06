# ADR-0001: Foundational Architecture Decisions

**Date:** 2026-04-06  
**Status:** Accepted  
**Deciders:** Greg Thomas  
**Source:** Derived from `PLAN.md` v1.0.0 (Draft)

---

## Context

Budget Sortbot was designed to run on resource-constrained consumer hardware — specifically a Raspberry Pi 3 or an equivalent spare NAS — without any cloud infrastructure. This hard deployment target drove almost every foundational technology choice: languages, frameworks, storage, and runtime configuration.

This ADR captures those foundational decisions so future contributors (human or agent) understand *why* the current shape of the system exists and do not inadvertently reverse choices that were deliberate constraints.

---

## Decisions

### 1. Runtime — JVM / Kotlin on Spring Boot 3

**Decision:** The backend is written in Kotlin 1.9, compiled for JVM 17, and runs on Spring Boot 3.3.

**Rationale:** The author is familiar with the JVM/Spring Boot ecosystem. Spring Boot provides opinionated auto-configuration for persistence (JPA/Flyway), scheduling, and HTTP serving, reducing the amount of boilerplate needed to reach a working system. Kotlin was chosen over Java for its null-safety and concise syntax.

**Trade-offs accepted:** The JVM has higher baseline memory usage than Go or native binaries. This is mitigated by the 512 MB heap cap and Serial GC tuning (see decision 2).

---

### 2. Memory Constraint — 512 MB JVM Heap, Serial GC

**Decision:** The JVM is started with `-Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`. This is enforced via `JAVA_TOOL_OPTIONS` in the Dockerfile and must not be increased.

**Rationale:** Raspberry Pi 3 has 1 GB of total RAM shared with the OS, Docker overhead, and SQLite. 512 MB is the practical maximum for a single JVM process on this hardware. Serial GC eliminates GC-thread overhead and is appropriate for single-core-equivalent workloads.

**Consequence:** Features or dependencies that require significant heap (e.g., large in-memory caches, heavy reflection at runtime, additional Spring contexts) must be avoided.

---

### 3. Architecture Style — Layered (Controller → Service → Infrastructure)

**Decision:** The application uses a three-layer architecture: Web (controllers/DTOs), Service (business logic), Infrastructure (external API clients + persistence repositories). No hexagonal/ports-and-adapters pattern.

**Rationale:** Hexagonal architecture was explicitly rejected in PLAN.md to keep the codebase approachable and avoid the additional abstraction overhead. The layered approach is sufficient for a single-tenant application with clearly bounded external integrations. Interfaces are still defined at the infrastructure boundary (e.g., `EmailProviderClient`, `YnabClient`, `ClassificationProvider`) to support testing with WireMock and swapping providers in the future without restructuring the full call graph.

---

### 4. Database — SQLite with Flyway Migrations

**Decision:** SQLite is the sole data store. Schema is managed by Flyway. Connection pool size is 1.

**Rationale:** SQLite requires no separate server process, has no network overhead, and its single-file storage model fits naturally on a volume-mounted Docker container. For a single-tenant, low-concurrency background job, SQLite's write serialisation is not a bottleneck. Flyway provides reproducible, version-controlled schema migrations without a migration server.

**Pool size = 1:** SQLite serialises writes internally. A larger pool causes contention and can produce `SQLITE_BUSY` errors; pool size 1 is the correct setting.

**Trade-offs accepted:** SQLite is not suitable if Budget Sortbot ever becomes a multi-tenant SaaS product with concurrent write load. The schema is designed with a future `tenant_id` column in mind to reduce the migration burden if that day comes.

---

### 5. Frontend — React SPA Co-Hosted by Spring Boot

**Decision:** The UI is a React 19 + TypeScript + Vite SPA, built at Docker image construction time and served as static files by Spring Boot from `src/main/resources/static/`. There is no separate frontend server or CDN.

**Rationale:** Co-hosting the SPA eliminates the need to run a second process or expose a second port, keeping resource usage and operational complexity low. A single Docker image is easier to deploy and update on constrained hardware.

**SPA routing:** Spring Boot's `WebConfig` uses `/{path:[^\\.]*}` to forward single-segment non-file paths to `index.html`. The Spring 6 `PathPatternParser` rejects `/**/{path}` patterns for this use case.

---

### 6. HTTP Client — Spring RestClient

**Decision:** All outbound HTTP calls (FastMail JMAP, YNAB API, Gemini API) use Spring's synchronous `RestClient`, not `WebClient` (reactive) or third-party clients (OkHttp, Feign).

**Rationale:** The application's sync pipeline runs on a scheduled thread pool, not in a reactive context. `RestClient` is the recommended synchronous client in Spring 6 / Spring Boot 3.2+, replacing the deprecated `RestTemplate`. Adding Feign or another HTTP client library was judged unnecessary given the small number of integration endpoints.

---

### 7. Email Provider — FastMail via JMAP

**Decision:** Email ingestion uses the FastMail JMAP API (`/.well-known/jmap`, `Email/query`, `Email/get`). The provider interface (`EmailProviderClient`) allows future substitution, but FastMail is the only supported implementation in v1.

**Rationale:** The author uses FastMail. JMAP is a modern, well-documented protocol that avoids the complexity of IMAP (including connection state management and library dependencies). Spring RestClient handles raw JSON over JMAP without a heavy library.

---

### 8. AI Classification — Google Gemini (`gemini-2.5-flash-lite`)

**Decision:** Category classification is performed by the Google Gemini API, specifically the `gemini-2.5-flash-lite` model. The provider interface (`ClassificationProvider`) allows future substitution.

**Rationale:** Gemini Flash Lite offers low latency and low cost per call. Classification only occurs when a YNAB transaction match has already been found, so the number of API calls per sync cycle is bounded. The zero-shot prompt approach (list order items + available categories) avoids fine-tuning cost and latency.

---

### 9. Tenancy — Single-Tenant v1, Schema Structured for v2

**Decision:** v1 is single-tenant. All DB tables omit a `tenant_id` column but are designed so one can be added as a non-breaking migration. No global static state is used so that a future auth middleware can inject tenant context per request.

**Rationale:** A multi-tenant SaaS product was out of scope for v1. Structuring the schema now avoids a costly refactor later.

---

### 10. Security — API Keys in DB (Plaintext), UI Not Internet-Exposed

**Decision:** YNAB, FastMail, and Gemini API keys are stored as plaintext in the `app_config` SQLite table. The UI is assumed to be on a private network and not exposed to the internet.

**Rationale:** Encryption-at-rest was deferred to a future version. The Pi 3 deployment target is typically on a home LAN behind a router, so the threat model for v1 does not require key encryption. This is explicitly noted as a v2 concern in PLAN.md.

---

### 11. Build and Deployment — Multi-Stage Docker, Multi-Arch

**Decision:** A three-stage Dockerfile builds the final image: Node.js stage (React build) → Gradle stage (backend JAR) → JRE runtime stage. The image is published for `linux/amd64`, `linux/arm64`, and `linux/arm/v7`.

**Rationale:** Multi-stage builds keep the final runtime image small (no build tools, no Node.js). Multi-arch support covers x86 desktops for development and ARM boards (Pi 3 = arm/v7, Pi 4/5 = arm64) for production.

---

## Consequences

- Technology choices are deliberately conservative and favour operational simplicity over scalability.
- Any story that requires more than ~400 MB active heap, introduces a reactive/async runtime, or requires a separate persistent process must be reviewed against the Pi 3 constraint.
- The layered architecture and interface-backed infrastructure layer keep the codebase testable without a running database or live external APIs (WireMock + in-memory SQLite covers all integration tests).
- Future multi-tenancy is possible without a full rewrite, but requires a schema migration and auth middleware additions.

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Go / native binary | Author unfamiliar; Spring Boot ecosystem reduces boilerplate significantly |
| PostgreSQL / MySQL | Requires a separate server process; not suitable for Pi 3 deployment |
| Separate frontend server (e.g., nginx) | Adds a second process and second port; increases operational complexity |
| WebFlux / reactive pipeline | No reactive I/O use case; scheduled sync is inherently sequential |
| OpenAI / Anthropic | Author uses Google Gemini; Flash Lite pricing is lower for this workload |
| IMAP for email ingestion | JMAP is more modern and avoids connection-state management complexity |
| Hexagonal architecture | Explicitly rejected to reduce abstraction overhead for a small, single-tenant app |
