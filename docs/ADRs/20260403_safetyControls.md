# ADR-0003: Safety Controls (Order Cap, Schedule, Start-From Date, Dry Run)

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #13 — Safety controls: order cap, configurable schedule, start-from date, dry run

---

## Context

PLAN.md specified a scheduled sync (`@Scheduled`) that would automatically process Amazon orders and update YNAB transactions. Early users activating the system for the first time faced two risks:

1. **Runaway writes** — no bound on how many historical orders would be processed in the first sync
2. **No preview** — no way to verify classification accuracy before committing writes to YNAB

PLAN.md did not specify any safety mechanisms for bounding or previewing the automated pipeline. These were added in PR #13 after the core pipeline (Phases 1–4) was already working.

---

## Decision

### Four Opt-In Guardrails

1. **Order cap** — `ORDER_CAP` (integer, default `0` = unlimited). `YnabSyncService` fetches all `PENDING` orders, filters by `orderDate >= startFromDate`, then applies `.take(orderCap)` before any YNAB API interaction. A cap of `10` processes at most 10 orders per sync cycle.

2. **Start-from date** — `START_FROM_DATE` (ISO date, default = installation date). Both `EmailIngestionService` and `YnabSyncService` filter by this cutoff:
   - Email ingestion: `sinceDate = max(lastSyncTime, startFromDate)` — emails before the cutoff are never ingested
   - YNAB sync: orders with `orderDate < startFromDate` are excluded before matching

3. **Configurable schedule** — `SCHEDULE_CONFIG` (JSON, default = `EVERY_N_HOURS` with `interval: 5`). Replaces hardcoded `@Scheduled` annotations with runtime-configurable cron expressions via `SyncScheduler` (single-thread `ThreadPoolTaskScheduler`). Schedule types: `EVERY_N_SECONDS`, `EVERY_N_MINUTES`, `HOURLY`, `EVERY_N_HOURS`, `DAILY`, `WEEKLY`. Changes take effect immediately via `SyncScheduler.reschedule()` — no container restart required.

4. **Dry run** — `DryRunService` runs the full pipeline (FastMail → match → Gemini classify) with **no YNAB writes**. Results stored in `dry_run_results` table (cleared on each trigger). Gemini failures per-order are captured as `errorMessage` without aborting the run. Triggered via `POST /api/config/dry-run`.

### Persistence

All four controls are persisted in the `app_config` table and seeded by Flyway migration `V2__safety_controls.sql`:

```sql
INSERT OR IGNORE INTO app_config (key, value, updated_at) VALUES
  ('ORDER_CAP', '0', CURRENT_TIMESTAMP),
  ('START_FROM_DATE', DATE('now'), CURRENT_TIMESTAMP),
  ('SCHEDULE_CONFIG', '{"type":"EVERY_N_HOURS","interval":5}', CURRENT_TIMESTAMP),
  ('INSTALLED_AT', DATETIME('now'), CURRENT_TIMESTAMP);
```

`INSTALLED_AT` is immutable and used to pre-populate the start-from date picker in the UI.

### Dynamic Scheduling

`SyncScheduler` replaces `@Scheduled` annotations. It holds a `ThreadPoolTaskScheduler` (pool size = 1, Pi 3 heap budget) and schedules both `EmailIngestionService.ingest()` and `YnabSyncService.sync()` at runtime based on `SCHEDULE_CONFIG`. Misconfigured JSON logs a warning and falls back to **no schedule** (not a default schedule) to prevent silent runaway syncs.

**`reschedule()` must be called after saving a new schedule** — `ConfigController.updateProcessingSettings()` calls it automatically.

### Dry Run Independence

Dry run has its own `startFromDate` parameter (defaults to 1 month before trigger time) independent of the live `START_FROM_DATE`. This allows users to preview classification on historical data without lowering the production cutoff.

### `EVERY_N_SECONDS` / `EVERY_N_MINUTES` Development Convenience

These schedule types are surfaced in the UI with a "not recommended for production" warning. They exist to allow dev/test cycles without external cron tooling or long waits. Production users are expected to use `HOURLY`, `EVERY_N_HOURS`, `DAILY`, or `WEEKLY`.

---

## Rationale

**Why four separate controls instead of a single "safe mode" toggle?**
- Users have different risk profiles: some want a daily schedule with no order cap; others want a 5-hour schedule capped at 5 orders
- Granular controls allow each user to calibrate their own safety threshold
- Dry run serves a distinct purpose (preview) vs. order cap (production bound)

**Why default `ORDER_CAP = 0` (unlimited)?**
- Existing PLAN.md behaviour was unlimited
- Users who activate the system after months of Amazon purchases may legitimately want to process 100+ historical orders
- The cap is opt-in; the default matches the original design

**Why `START_FROM_DATE` defaults to installation date, not 1970-01-01?**
- Most users are activating the system on a specific date because they want automation *going forward*
- Processing years of historical orders is rare and risky (data quality, API rate limits)
- Users who want full backfill can explicitly set the date to `2020-01-01` or earlier

**Why `SyncScheduler` instead of keeping `@Scheduled`?**
- `@Scheduled` cron expressions are compile-time constants; Spring does not support runtime cron changes without recreating the context
- `ThreadPoolTaskScheduler` allows `cancelScheduledFuture()` + `schedule(task, trigger)` at runtime
- Pool size = 1 prevents overlapping sync runs on slow hardware

**Why fall back to *no schedule* on misconfiguration instead of a default?**
- A misconfigured schedule is a user error (e.g., hand-editing the DB)
- Silently falling back to a 5-hour schedule could cause runaway writes if the user intended a daily schedule
- Logging a warning and stopping syncs until the config is fixed is safer

**Why separate `startFromDate` for dry run?**
- Dry run is a preview tool; users want to test classification on historical orders they've already processed
- Forcing dry run to respect the live `START_FROM_DATE` would make it useless for verifying backfill accuracy
- Independence allows "what-if" scenarios without changing production config

**Why allow `EVERY_N_SECONDS` in production at all?**
- E2E tests need fast schedules to make background jobs observable within Playwright's timeout window
- The UI warning makes the risk clear; blocking it entirely would require a separate test-only code path

---

## Consequences

- Users activating Budget Sortbot for the first time are protected from runaway YNAB writes by the start-from date default (today)
- Order cap provides an incremental rollout path: set cap to 5, verify classification accuracy, raise to 10, repeat
- Dry run output must be manually reviewed before activating live syncs — there is no auto-approval flow
- Changing the schedule via the UI takes effect immediately without a container restart, but changing it via direct DB edit requires calling `reschedule()` or restarting the app
- `EVERY_N_SECONDS` / `EVERY_N_MINUTES` schedules create API rate limit risk if set to very low intervals (e.g., 1 second); the UI warning is the only mitigation
- Any story that adds a new scheduled job must wire it into `SyncScheduler`, not use `@Scheduled`

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Single "safe mode" toggle | Too coarse; users need granular control over cap vs. schedule vs. cutoff date |
| Order cap defaults to 10 | Breaking change for users who expect unlimited processing; opt-in is safer |
| Hardcoded 5-hour `@Scheduled` cron | No runtime configurability; users would need to rebuild the Docker image to change the schedule |
| Separate dry-run service with its own scheduler | Dry run is one-shot; scheduled dry runs are out of scope for v1 |
| Spring Boot Actuator `/actuator/scheduledtasks` | Read-only; does not support runtime schedule changes |
| Kubernetes CronJob for scheduling | Out of scope; Budget Sortbot targets Pi 3 / Docker Compose deployments, not k8s |
| Quartz scheduler | Heavyweight; introduces a persistent job store and cluster coordination features not needed for a single-tenant app |
