# Budget Sortbot

*Automatically classify your Amazon orders into YNAB budget categories*

> **Security notice:** The management UI has no authentication. Do **not** expose port 8080 to the public internet.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [First-Run Checklist](#first-run-checklist)
4. [Configuration Reference](#configuration-reference)
5. [Debugging & Log Retrieval](#debugging--log-retrieval)
6. [Getting Help](#getting-help)

---

## Prerequisites

| Requirement | Notes |
|---|---|
| [Docker](https://docs.docker.com/get-docker/) | Any recent version; multi-arch image supports `linux/amd64`, `linux/arm64`, `linux/arm/v7` (Raspberry Pi 3+) |
| YNAB account | A Personal Access Token and your Budget ID — see [YNAB API docs](https://api.youneedabudget.com/#personal-access-tokens) |
| FastMail account | A FastMail API token with email read access — see [FastMail API tokens](https://www.fastmail.com/settings/privacy-security/tokens) |
| Google Gemini API key | Available from [Google AI Studio](https://aistudio.google.com/app/apikey) |
| A host directory for data | The container stores its SQLite database here so config survives restarts |

---

## Quick Start

Create a directory on your host to persist the database, then start the container:

```bash
# Linux / macOS
mkdir -p /opt/budget-sortbot/data
docker run -d \
  --name budget-sortbot \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /opt/budget-sortbot/data:/app/data \
  ghcr.io/gthoma17/budget-sortbot:latest
```

> **Windows (Command Prompt):** Use a Windows-style host path, for example:
> ```
> docker run -d --name budget-sortbot --restart unless-stopped -p 8080:8080 -v C:\budget-sortbot\data:/app/data ghcr.io/gthoma17/budget-sortbot:latest
> ```
>
> **Windows (PowerShell):** Replace backslashes with forward slashes or wrap the path in quotes.

The management UI is available at **http://localhost:8080** once the container is running.

The SQLite database (`/app/data/database.sqlite`) is stored in the mounted volume, so all configuration and logs are preserved across container restarts and image upgrades.

---

## First-Run Checklist

1. **Open the UI** — navigate to `http://localhost:8080` in your browser.
2. **Enter your credentials** — go to the **Configuration** page and fill in all required fields (see [Configuration Reference](#configuration-reference) below).
3. **Set the start date** — choose the earliest Amazon order date you want to import so the first email scan doesn't pull years of history.
4. **Set the order cap** — start with a small number (e.g. `5`) to verify the pipeline works before processing large volumes.
5. **Save** — the app writes everything to the SQLite database; no restart is needed.
6. **Verify the scheduler** — the **System Logs** page shows each scheduled run. Wait for the next scheduled run (default: every 5 hours) or trigger a **Dry Run** from the Configuration page to test without writing to YNAB.

---

## Configuration Reference

All settings are managed through the **Configuration** page in the UI.

| Setting (UI label) | Section | Description | Where to find it |
|---|---|---|---|
| **YNAB Token** | YNAB | Personal Access Token used to authenticate with the YNAB API | [YNAB → My Account → Developer Settings](https://app.youneedabudget.com/settings/developer) |
| **Budget ID** | YNAB | The UUID of the YNAB budget to update | Selected from the **Budget** dropdown on the Configuration page — populated automatically after you enter a valid YNAB token |
| **FastMail API Token** | FastMail | A FastMail API token with email read access | FastMail → Settings → Privacy & Security → API tokens → New token — enable the **Email** scope checkbox and set the toggle to **Read only** |
| **Gemini Key** | Gemini | Google Gemini API key used to classify order descriptions | [Google AI Studio → API Keys](https://aistudio.google.com/app/apikey) |
| **Max orders per run** | Processing Settings | Maximum number of orders processed per sync run (`0` = unlimited) | Start with a small value (e.g. `5`) during initial testing |
| **Sync schedule** | Processing Settings | How often the sync runs; choose a frequency from the dropdown | Configured via the Sync schedule section on the Configuration page |
| **Start from date** | Processing Settings | Earliest order date to import; orders before this date are ignored | Set once during first-run setup |

---

## Debugging & Log Retrieval

### Sync Logs view (first stop)

Before diving into container logs, check the **System Logs** page in the UI. It shows a summary of every sync run including status, timestamp, and a short description of what happened — this is usually enough to diagnose common issues.

### View container logs

```bash
docker logs budget-sortbot
```

To follow the log output in real time:

```bash
docker logs -f budget-sortbot
```

To see only the last 200 lines:

```bash
docker logs --tail 200 budget-sortbot
```

### Enable DEBUG-level logging (no rebuild required)

Spring Boot reads logging levels from environment variables at startup. Stop and recreate the container with the `LOGGING_LEVEL_COM_BUDGETSORTBOT=DEBUG` variable to enable verbose logging for the application:

```bash
docker stop budget-sortbot
docker rm budget-sortbot

# Linux / macOS
docker run -d \
  --name budget-sortbot \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /opt/budget-sortbot/data:/app/data \
  -e LOGGING_LEVEL_COM_BUDGETSORTBOT=DEBUG \
  ghcr.io/gthoma17/budget-sortbot:latest
```

No image rebuild is needed. Remove the `-e LOGGING_LEVEL_COM_BUDGETSORTBOT=DEBUG` flag and recreate the container to return to the default `INFO` level.

### What to look for in the logs

| Flow | Log markers to watch for |
|---|---|
| **Email ingestion** | Messages referencing `EmailIngestionService` or `FastMailClient` — look for the number of emails fetched and any parsing errors |
| **YNAB sync** | Messages referencing `YnabSyncService` or `YnabClient` — look for matched transactions and any API errors |
| **AI classification** | Messages referencing `ClassificationService` or `GeminiClient` — look for category assignments and any quota/rate-limit errors |
| **Scheduler** | Messages referencing `SyncScheduler` — show each scheduled trigger and whether the run completed successfully |

---

## Getting Help

If you run into a problem, start with the **Get Help** page in the UI (navigate to **http://localhost:8080/help**). It lets you describe the problem, automatically attaches relevant sync logs with sensitive values redacted, and opens a pre-filled GitHub issue in your browser.

If you prefer to open an issue manually:

1. Open an issue at **https://github.com/gthoma17/budget-sortbot/issues/new***
2. Include the following in your report:
   - The relevant output from the **System Logs** page in the UI
   - A snippet from `docker logs budget-sortbot` covering the failed run
   - The steps you took and what you expected vs. what happened
   - Your host OS and Docker version (`docker version`)

Please **redact** any API keys, passwords, or personal email addresses before sharing logs.
