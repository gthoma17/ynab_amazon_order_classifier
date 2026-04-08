# ADR-0003: Dynamic Scheduling with SyncScheduler

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #13 — Safety controls: order cap, configurable schedule, start-from date, dry run

---

## Context

PLAN.md specified `@Scheduled` cron annotations for email ingestion and YNAB sync. Hardcoded cron expressions cannot be changed without rebuilding the Docker image, and Spring does not support runtime cron changes without recreating the application context.

---

## Decision

**Replace `@Scheduled` annotations with `SyncScheduler` — a single-thread `ThreadPoolTaskScheduler` that reads cron config from the database and supports runtime reschedule without app restart.**

### Implementation

`SyncScheduler` (see `src/main/kotlin/com/budgetsortbot/service/SyncScheduler.kt`) uses a single-thread `ThreadPoolTaskScheduler` (pool size = 1 for Pi 3 heap budget). The `reschedule()` method cancels existing tasks, reads cron config from the database, and schedules both `emailIngestionService.ingest()` and `ynabSyncService.sync()` with a `CronTrigger`.

Schedule stored as JSON in `app_config`:

```json
{"type":"EVERY_N_HOURS","interval":5}
```

`ScheduleConfig.toCron()` converts to Spring 6-field cron. Misconfigured JSON logs a warning and falls back to **no schedule** (not a default) to prevent runaway writes.

### Runtime Reschedule

`ConfigController.updateProcessingSettings()` calls `syncScheduler.reschedule()` after saving the new schedule. Changes take effect immediately without container restart.

**Pool size = 1** prevents overlapping sync runs on slow hardware. Both services run on the same schedule; staggering is not required (email ingestion completes before YNAB sync starts).

---

## Consequences

- Schedule changes via UI take effect immediately without container restart
- Misconfigured schedule stops all syncs until fixed (logged warning)
- Any new scheduled job must wire into `SyncScheduler`, not use `@Scheduled`
- Single-thread pool serialises all background work; concurrent execution is not supported

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Keep `@Scheduled` with env var cron | Env vars cannot change without container restart; defeats the purpose |
| Spring Boot Actuator `/actuator/scheduledtasks` | Read-only; does not support runtime changes |
| Quartz scheduler | Heavyweight; persistent job store not needed for single-tenant |
| Separate schedulers per service | Pool size = 2 wastes heap; both services run on the same cadence |
