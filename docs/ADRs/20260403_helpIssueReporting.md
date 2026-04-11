# ADR-0007: Help/Issue Reporting Security and Data Control

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #23 — Add "Get Help" page with sanitized GitHub issue pre-fill

---

## Context

Users encountering problems needed a low-friction path to file bug reports with logs attached. Two security/privacy concerns emerged:

1. **Credential leakage** — Logs might contain API tokens from error messages or debug output
2. **Data control** — Users should retain full control over what data leaves their system

---

## Decision

### Server-Side Credential Sanitization

**All redaction happens server-side before the response leaves the JVM.** `HelpController` queries all `app_config` values, sorts by length descending, and replaces any occurrence in the report body with `[REDACTED]`:

```kotlin
val configValues = configService.getAllValues().values.sortedByDescending { it.length }
var sanitized = reportBody
configValues.forEach { value ->
    sanitized = sanitized.replace(value, "[REDACTED]", ignoreCase = true)
}
```

Longest-first sorting prevents partial-match leaks (e.g., if both `abc-123` and `abc-123-secret` exist, `abc-123-secret` is redacted first).

The frontend **never sees raw sensitive values**. It receives only the pre-sanitized body.

### GitHub URL with Query Params (Not API Call)

**Users retain full control of their data** by using `window.open(githubIssuesUrl?body=encodeURIComponent(sanitizedBody))` instead of an API call that auto-creates issues on their behalf.

**Rationale:**
- Users can review the pre-filled body before submitting
- Users can edit the description or add context
- Users can cancel without creating an issue
- No OAuth flow required (GitHub new-issue page is public)

**Tradeoff accepted:** GitHub URL limit (~8000 chars) requires truncation. Backend truncates at 4000 chars before sanitization, appends `[truncated — use docker logs for full output]` if needed. Frontend calculates final URL length and trims further if necessary to stay under `MAX_GITHUB_URL_LENGTH`.

---

## Consequences

- API credentials are redacted server-side; no client-side sanitization required
- Users control when/if issues are filed; no OAuth or GitHub API token needed
- Reports longer than ~8000 chars (URL-encoded) are truncated; users directed to `docker logs` for full output
- Adding a new sensitive config key automatically includes it in redaction (all `app_config` values are redacted by default)

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Client-side redaction | Requires fetching all config values to the browser; defeats the purpose |
| GitHub API auto-create issue | Removes user control; requires OAuth flow; users can't review before submit |
| Email-based bug reports | Requires SMTP config; increases operational complexity |
| Unlimited report length via API | GitHub URL limit is ~8000 chars; API call would remove user control |
