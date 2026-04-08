# ADR-0008: Configuration UX Improvements (FastMail Single Token, YNAB Budget Dropdown)

**Date:** 2026-04-03
**Status:** Accepted
**Deciders:** Greg Thomas
**Source:** PR #21 (FastMail), PR #24 (YNAB budget dropdown)

---

## Context

The initial Configuration page (Phase 4) collected five API credentials across three integrations. Two UX friction points emerged:

1. **FastMail JMAP required username + password/token** — users had to enter both fields, but the username was never actually used in JMAP requests (only the token appeared in `Authorization` headers)
2. **YNAB budget ID was a free-text UUID field** — users had to manually locate their budget UUID from the YNAB app URL (e.g., `https://app.ynab.com/<budget-id>/budget`) and copy-paste it, error-prone and opaque

---

## Decision

### FastMail: Single API Token (PR #21)

**Remove `FASTMAIL_USER`, rename `FASTMAIL_TOKEN` → `FASTMAIL_API_TOKEN`.**

**Backend changes:**
- `ConfigService`: removed `FASTMAIL_USER` constant
- `EmailProviderClient.searchOrders()`: dropped `user: String` parameter (was never used)
- `FastMailJmapClient`: `accountId` already discovered dynamically from JMAP session response (`/.well-known/jmap`); the username parameter was dead code

**Frontend changes:**
- `ConfigView`: FastMail section reduced to single `` for API token
- "Test FastMail" button enables when token is non-empty (was: both user and token required)

**API/DTO changes:**
- `ApiKeysRequest` / `ApiKeysResponse`: `fastmailUser` + `fastmailToken` fields replaced with single `fastmailApiToken` field

**README update:**
- FastMail instructions now say "generate an API token (Settings → Privacy & Security → API tokens, **Email** scope + **Read only** toggle)"

### YNAB: Budget Dropdown (PR #24)

**Replace free-text budget ID input with a dropdown populated from `GET /budgets` using the token already present in the form.**

**Backend changes:**
- Added `getBudgets(token): List<YnabBudget>` to `YnabClient` / `YnabRestClient`
- New endpoint `GET /api/ynab/budgets?token=` — accepts an explicit token (for unsaved tokens) or falls back to the stored token from `ConfigService`:

```kotlin
@GetMapping("/budgets")
fun getBudgets(@RequestParam(required = false) token: String?): List<YnabBudgetResponse> {
    val effectiveToken = token?.takeIf { it.isNotBlank() }
        ?: configService.getValue(ConfigService.YNAB_TOKEN)
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "YNAB token not configured")
    return ynabClient.getBudgets(effectiveToken).map { YnabBudgetResponse(it.id, it.name) }
}
```

**Frontend changes:**
- `ConfigView` fetches `/api/ynab/budgets?token=` in a `useEffect` keyed on `keys.ynabToken` — fires on mount (saved token) and on token change (unsaved token)
- Budget ID `` replaced with `<select>` covering all states:
  - **No token** — disabled with placeholder "Enter YNAB token first"
  - **Loading** — spinner label, select disabled
  - **Loaded** — enabled; saved `ynabBudgetId` UUID pre-selected if present in returned list
  - **Error** — inline `role="alert"` message
  - **Empty** — disabled "No budgets found" option
- Stored/emitted value remains the UUID; only the display changes

**README update:**
- Budget ID instructions replaced with "select your budget from the dropdown (auto-populated once you enter a valid YNAB token)"

---

## Rationale

### FastMail Single Token

**Why remove the username field entirely instead of making it optional?**
- The username was never used — `FastMailJmapClient` discovered `accountId` from the JMAP session response
- Optional fields confuse users ("do I need this or not?")
- Removing it simplifies onboarding and reduces the API surface

**Why rename `FASTMAIL_TOKEN` → `FASTMAIL_API_TOKEN`?**
- "Token" is ambiguous (password? session token? API token?)
- "API Token" matches the FastMail UI label (Settings → API tokens)
- Clearer intent for future contributors

### YNAB Budget Dropdown

**Why fetch budgets using the unsaved token instead of requiring the user to save first?**
- Immediate feedback: user enters token, dropdown populates instantly
- No round-trip to the backend to save → reload → fetch budgets
- Better UX: user can verify the token is valid before clicking Save

**Why fall back to the stored token if `?token=` is not provided?**
- On page reload, the token field may be pre-filled from the stored value
- Fetching budgets on mount allows returning users to see their budget selected immediately
- `?token=` param is used when the user types a new token; stored token is used on mount

**Why keep the budget ID as a UUID internally instead of storing the budget name?**
- YNAB API requires the UUID in all transaction/category requests
- Budget names can change (user renames "My Budget" → "2026 Budget")
- Storing the UUID ensures API calls never break due to a rename

**Why disable the dropdown when the token is empty?**
- Fetching `/api/ynab/budgets` without a token would fail with 401
- Disabled state with placeholder makes the dependency explicit

**Why POST the token in the request body (initially a GET query param)?**
- YNAB tokens are sensitive; GET query params are logged in server access logs
- POST body is not logged by default
- Security improvement made during v1.0.0 release (PR #29)

---

## Consequences

### FastMail
- Users only need to generate one API token (FastMail Settings → Privacy & Security → API tokens → Email scope, Read only)
- Onboarding friction reduced by one input field
- `FASTMAIL_USER` is removed from the database; existing installs with a stored `FASTMAIL_USER` will ignore it (config service falls back to null if key is missing)
- Any code referencing `FASTMAIL_USER` will break at compile time (intentional — ensures no dead code paths remain)

### YNAB Budget Dropdown
- Users no longer need to manually locate their budget UUID from the YNAB app URL
- Budget dropdown populates automatically when a valid YNAB token is entered
- `/api/ynab/budgets` is a new endpoint that returns budget metadata; could be rate-limited by YNAB (unlikely — read-only, called once per config page load)
- Saved budget ID remains a UUID; the dropdown is display-only sugar

---

## Alternatives Not Chosen

| Alternative | Reason Rejected |
|---|---|
| Keep FastMail username as an optional field | Optional fields confuse users; the username was never used, so removing it entirely is clearer |
| Use budget name instead of UUID internally | Budget names can change; YNAB API requires UUIDs; storing names would break API calls on rename |
| Require user to save YNAB token before fetching budgets | Adds a round-trip; immediate feedback (dropdown populates as soon as token is entered) is better UX |
| Fetch budgets on form submit instead of on token change | User would have to click Save to see budgets; live feedback is more intuitive |
| GET `/api/ynab/budgets?token=` with token in query param | Tokens in query params are logged in server access logs; POST body is safer (fixed in PR #29) |
| Multiselect for budgets | Budget Sortbot is single-tenant; each instance manages one budget; multiselect is out of scope |
