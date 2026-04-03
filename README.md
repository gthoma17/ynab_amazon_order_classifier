# YNAB Amazon Order Classifier

Automatically categorize Amazon order transactions in [YNAB](https://www.youneedabudget.com/) by parsing Amazon order-confirmation emails via FastMail and classifying them with Google Gemini.

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
| FastMail account | Your FastMail email address and a dedicated [JMAP App Password](https://www.fastmail.com/settings/security/passwords) |
| Google Gemini API key | Available from [Google AI Studio](https://aistudio.google.com/app/apikey) |
| A host directory for data | The container stores its SQLite database here so config survives restarts |

---

## Quick Start

Create a directory on your host to persist the database, then start the container:

```bash
# Linux / macOS
mkdir -p /opt/ynab-auto/data
docker run -d \
  --name ynab-automator \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /opt/ynab-auto/data:/app/data \
  ghcr.io/gthoma17/ynab-automator:latest
```

> **Windows (Command Prompt):** Use a Windows-style host path, for example:
> ```
> docker run -d --name ynab-automator --restart unless-stopped -p 8080:8080 -v C:\ynab-auto\data:/app/data ghcr.io/gthoma17/ynab-automator:latest
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

All settings are stored in the `app_config` table and managed through the **Configuration** page in the UI.

| Key | Description | Where to find it |
|---|---|---|
| `YNAB_TOKEN` | Personal Access Token used to authenticate with the YNAB API | [YNAB → My Account → Developer Settings](https://app.youneedabudget.com/settings/developer) |
| `YNAB_BUDGET_ID` | The UUID of the YNAB budget to update | Visible in the URL when you open a budget: `https://app.youneedabudget.com/<budget-id>/…` |
| `FASTMAIL_USER` | Your FastMail email address (e.g. `you@fastmail.com`) | Your FastMail login |
| `FASTMAIL_TOKEN` | A FastMail JMAP App Password (not your account password) | FastMail → Settings → Security → Passwords → New App Password |
| `GEMINI_KEY` | Google Gemini API key used to classify order descriptions | [Google AI Studio → API Keys](https://aistudio.google.com/app/apikey) |
| `ORDER_CAP` | Maximum number of orders processed per sync run (safety limit; `0` disables syncing) | Set to a small value during initial testing |
| `SCHEDULE_CONFIG` | JSON object controlling how often the sync runs (e.g. `{"type":"EVERY_N_HOURS","hourInterval":5}`) | Managed via the Schedule section of the Configuration page |
| `START_FROM_DATE` | Earliest order date to import (`YYYY-MM-DD`); orders before this date are ignored | Set once during first-run setup |

---

## Debugging & Log Retrieval

### View container logs

```bash
docker logs ynab-automator
```

To follow the log output in real time:

```bash
docker logs -f ynab-automator
```

To see only the last 200 lines:

```bash
docker logs --tail 200 ynab-automator
```

### Enable DEBUG-level logging (no rebuild required)

Spring Boot reads logging levels from environment variables at startup. Stop and recreate the container with the `LOGGING_LEVEL_COM_YNABAUTO=DEBUG` variable to enable verbose logging for the application:

```bash
docker stop ynab-automator
docker rm ynab-automator

# Linux / macOS
docker run -d \
  --name ynab-automator \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /opt/ynab-auto/data:/app/data \
  -e LOGGING_LEVEL_COM_YNABAUTO=DEBUG \
  ghcr.io/gthoma17/ynab-automator:latest
```

No image rebuild is needed. Remove the `-e LOGGING_LEVEL_COM_YNABAUTO=DEBUG` flag and recreate the container to return to the default `INFO` level.

### What to look for in the logs

| Flow | Log markers to watch for |
|---|---|
| **Email ingestion** | Messages referencing `EmailIngestionService` or `FastMailClient` — look for the number of emails fetched and any parsing errors |
| **YNAB sync** | Messages referencing `YnabSyncService` or `YnabClient` — look for matched transactions and any API errors |
| **AI classification** | Messages referencing `ClassificationService` or `GeminiClient` — look for category assignments and any quota/rate-limit errors |
| **Scheduler** | Messages referencing `SyncScheduler` — show each scheduled trigger and whether the run completed successfully |

### Sync Logs view (first stop)

Before diving into container logs, check the **System Logs** page in the UI. It shows a summary of every sync run including status, timestamp, and a short description of what happened — this is usually enough to diagnose common issues.

---

## Getting Help

If you run into a problem that you cannot resolve from the logs:

1. Open an issue at **https://github.com/gthoma17/ynab_amazon_order_classifier/issues**
2. Include the following in your report:
   - The relevant output from the **System Logs** page in the UI
   - A snippet from `docker logs ynab-automator` covering the failed run
   - The steps you took and what you expected vs. what happened
   - Your host OS and Docker version (`docker version`)

Please **redact** any API keys, passwords, or personal email addresses before sharing logs.