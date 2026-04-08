# ADR-0007: Help/Issue Reporting System (Sanitized GitHub Issue Pre-Fill)

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #23 — Add "Get Help" page with sanitized GitHub issue pre-fill

---

## Context

Early users encountering problems had no low-friction path to report them. Filing a GitHub issue required:
1. Manually copying `docker logs` output or sync logs from the UI
2. Manually redacting API credentials before pasting into the issue body
3. Writing a description of the problem
4. Navigating to GitHub Issues and pasting everything together

This high-friction path resulted in incomplete bug reports (missing logs, no redaction, vague descriptions).

---

## Decision

### In-App "Get Help" Page

A dedicated `/help` route in the management console that:
1. Accepts a user-written description (required)
2. Accepts an "include sync logs" checkbox (on by default)
3. Calls `POST /api/help/report` to generate a sanitized Markdown body
4. Opens a GitHub new-issue URL (`window.open`) with the body pre-filled

### Backend — `HelpController` + `ReportSanitizationService`

**`POST /api/help/report`** accepts:
- `description: String` (required, validated non-blank)
- `includeSyncLogs: Boolean` (default `true`)

**Assembles a Markdown body:**
1. User description at the top
2. Up to 50 recent `sync_logs` rows (DESC by `lastRun`) formatted as a Markdown table
3. Footer directing users to `docker logs` for application logs (v1 defers app-log inclusion to v2)

**Sanitization:**
- Queries all `app_config` values
- Sorts by length descending (prevents partial-overlap replacements, e.g., redacting `key-suffix` before `api-key-suffix` would leave `api-` visible)
- Replaces any occurrence of a config value in the body with `[REDACTED]`
- **Does not redact:** `ORDER_CAP`, `SCHEDULE_CONFIG`, `START_FROM_DATE`, `INSTALLED_AT` (configuration metadata, not secrets)
- **Redacts by default:** All other keys (`YNAB_TOKEN`, `FASTMAIL_API_TOKEN`, `GEMINI_KEY`, `YNAB_BUDGET_ID`)
- Returns `(body: String, sanitized: Boolean)` so the frontend can surface a notice if any values were redacted

**Truncation:**
- GitHub issue URLs have a practical limit of ~8000 chars (browser URL bar + server limits)
- Backend truncates the assembled body to 4000 chars before sanitization
- Appends `[truncated — use docker logs for full output]` if truncation occurred
- Frontend calculates the final URL length after encoding and trims to `MAX_GITHUB_URL_LENGTH - GITHUB_BASE_URL_LEN` if needed

### Frontend — `GetHelpView`

**Form fields:**
- `<textarea>` for description (required; button disabled when empty)
- Checkbox for "include sync logs" (checked by default)
- "Get Help" button

**Flow:**
1. User fills description, optionally unchecks sync logs, clicks "Get Help"
2. `POST /api/help/report` → receives sanitized body
3. `window.open(githubIssuesUrl?body=encodeURIComponent(sanitizedBody))`
4. Shows a notice if `response.sanitized === true` ("API keys have been redacted")

**Error handling:**
- Displays error message if API call fails (network error, validation failure)
- Falls back gracefully without opening a GitHub tab

### Security

**All redaction happens server-side.** The frontend never sees raw sensitive values at any point in the flow. The backend:
1. Queries `app_config`
2. Sanitizes the report body
3. Returns the sanitized body to the frontend
4. Frontend directly calls `window.open` with the sanitized body

The raw config values never leave the JVM.

---

## Rationale

**Why server-side redaction instead of client-side?**
- Client-side redaction requires fetching all `app_config` values to the browser (defeats the purpose of redaction)
- Server-side ensures sensitive values never cross the HTTP boundary
- Backend can apply sophisticated redaction logic (longest-first sorting, partial match prevention)

**Why only redact some config keys?**
- `ORDER_CAP`, `SCHEDULE_CONFIG`, `START_FROM_DATE`, `INSTALLED_AT` are diagnostic metadata ("why did 100 orders process?" → order cap is 0)
- API tokens are credentials that could be abused if leaked in a public GitHub issue
- Conservative default: redact everything except known-safe metadata

**Why longest-first sorting for redaction?**
- Prevents partial-match leaks: if `YNAB_BUDGET_ID=abc-123` and `YNAB_TOKEN=abc-123-secret`, redacting `abc-123` first would leave `abc-123-secret` → `[REDACTED]-secret`
- Sorting descending by length ensures `abc-123-secret` is redacted first

**Why `window.open` instead of rendering the issue body in the UI?**
- Users expect to file issues on GitHub, not in the Budget Sortbot UI
- `window.open` preserves browser context (GitHub auth, issue templates, labels)
- One-click flow: click "Get Help" → GitHub new-issue page opens with body pre-filled

**Why defer application logs to v2?**
- `sync_logs` covers business-level audit trail (email ingest, YNAB sync, Gemini classify)
- Application logs (DEBUG/INFO/WARN/ERROR) require Blacklite integration (added in PR #43)
- V1 footers direct users to `docker logs` for framework-level errors

**Why truncate at 4000 chars before sanitization?**
- URL-encoded body doubles in size (`&` → `%26`, etc.)
- 4000 chars pre-encoding → ~8000 chars post-encoding (GitHub URL limit)
- Truncating after sanitization could cut mid-`[REDACTED]` token and leak partial secrets

**Why include up to 50 sync logs instead of all logs?**
- Most users have < 50 sync runs before encountering a problem
- Including all logs risks hitting the URL length limit
- 50 rows is enough to show recent history (last ~10 days at default 5-hour schedule)

---

## Consequences

- Users can file GitHub issues with one click; no manual log copying or redaction required
- All bug reports include sync logs by default (email/YNAB success/failure audit trail)
- API credentials are redacted server-side before the response leaves the JVM; no client-side sanitization required
- Reports longer than 4000 chars are truncated with an inline note; users are directed to `docker logs` for full output
- Configuration metadata (`ORDER_CAP`, `SCHEDULE_CONFIG`, `START_FROM_DATE`, `INSTALLED_AT`) is **not** redacted and appears in public GitHub issues — acceptable because these are not credentials
- Application logs (DEBUG/WARN/ERROR) are not included in v1 reports; users must manually copy-paste `docker logs` output for framework-level errors (v2 deferred to PR #43 Blacklite integration)

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Client-side redaction | Requires fetching all config values to the browser; defeats the purpose of redaction |
| Redact all config values including metadata | `ORDER_CAP`, `SCHEDULE_CONFIG`, `START_FROM_DATE` are diagnostic; redacting them makes issues harder to debug |
| Render issue body in the UI instead of `window.open` | Users expect to file issues on GitHub; in-app rendering would require reimplementing GitHub's issue form |
| Include application logs in v1 | Requires Blacklite integration (PR #43); deferred to v2 to unblock the help page feature |
| Unlimited report length | GitHub URL limit is ~8000 chars; exceeding it produces a 414 Request-URI Too Large error |
| Email-based bug reports | Requires SMTP config; increases operational complexity; GitHub issues are the canonical bug tracker |
| Copy-to-clipboard button instead of `window.open` | Adds a step (user must manually navigate to GitHub and paste); `window.open` is one-click |
