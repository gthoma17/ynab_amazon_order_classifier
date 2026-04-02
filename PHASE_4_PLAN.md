# Phase 4: Frontend Implementation Plan

## Overview
Build a React SPA that talks to the existing Spring Boot backend REST API and configure Spring to serve it statically. Follow strict TDD: write tests first, confirm they fail, then implement until green, then refactor.

## Stack
- **Framework:** React 18 + TypeScript (Vite)
- **Testing:** Vitest + React Testing Library + jsdom + @testing-library/jest-dom + @testing-library/user-event + MSW (Mock Service Worker) for API mocking
- **HTTP:** native `fetch` (browser API)
- **Routing:** React Router v6 (SPA navigation)
- **Build location:** `frontend/` directory; output copied to `src/main/resources/static/` for Spring

## TDD Loop Checklist

### Step 0 — Plan (this file)
- [x] Create PHASE_4_PLAN.md, commit, open PR

### Step 1 — Write failing tests
- [ ] Scaffold Vite React+TS project under `frontend/`
- [ ] Add testing deps: vitest, jsdom, @testing-library/react, @testing-library/jest-dom, @testing-library/user-event, msw
- [ ] Configure `vitest.config.ts` and `setupTests.ts`
- [ ] Write `App.test.tsx`: navigation links render and pages route correctly
- [ ] Write `ConfigView.test.tsx`: renders key fields, loads values from API, saves on submit
- [ ] Write `CategoryRulesView.test.tsx`: lists rules from API, lets user edit descriptions, saves on submit
- [ ] Write `PendingOrdersView.test.tsx`: renders table rows of pending orders from API
- [ ] Write `LogsView.test.tsx`: renders table rows of sync logs from API
- [ ] Commit test-only files

### Step 2 — Confirm tests fail
- [ ] Run `npm test` in `frontend/`, verify new tests fail (components don't exist)
- [ ] Confirm build completes and tests are reached

### Step 3 — Resolve compile/dependency issues
- [ ] Fix any import or TS config issues in test files only
- [ ] Re-run until all tests compile and execute

### Step 4 — Confirm tests fail for code reasons
- [ ] Verify each test fails at assertion (missing component/UI), not at compile/config
- [ ] Commit if any Step 3 changes were needed

### Step 5 — Make tests pass
- [ ] Implement `App.tsx` with React Router (4 routes + nav bar)
- [ ] Implement `ConfigView.tsx`: form for YNAB Token, Budget ID, FastMail User, FastMail Token, Gemini Key
- [ ] Implement `CategoryRulesView.tsx`: fetches YNAB categories, shows editable descriptions, PUT on save
- [ ] Implement `PendingOrdersView.tsx`: fetches pending orders, renders table
- [ ] Implement `LogsView.tsx`: fetches sync logs, renders table
- [ ] Run tests after each component, iterate until all pass
- [ ] Commit implementation files

### Step 6 — Refactor
- [ ] Refactor shared API fetch logic into a reusable `api.ts` module
- [ ] Improve naming and remove duplication
- [ ] Run tests to confirm all still green
- [ ] Commit refactored code

### Step 7 — Spring SPA Fallback
- [ ] Add `WebConfig.kt` to forward all non-API, non-static requests to `index.html`
- [ ] Write `WebConfigTest.kt` (Spring MVC test) verifying the fallback mapping
- [ ] Build React app (`npm run build`), copy output to `src/main/resources/static/`
- [ ] Run full Kotlin test suite to confirm no regressions
- [ ] Delete PHASE_4_PLAN.md
- [ ] Final commit

## API Endpoints Used by Frontend
| View | Method | Endpoint |
|------|--------|----------|
| Config Keys | GET | `/api/config/keys` |
| Config Keys | PUT | `/api/config/keys` |
| Category Rules | GET | `/api/config/categories` |
| Category Rules | PUT | `/api/config/categories` |
| YNAB Categories | GET | `/api/ynab/categories` |
| Pending Orders | GET | `/api/orders/pending` |
| Sync Logs | GET | `/api/logs` |

## Spring WebConfig
Add `WebConfig.kt` that implements `WebMvcConfigurer` and registers a resource handler plus a view controller that forwards `/**` (excluding `/api/**`) to `/index.html`. This enables deep-link navigation in the SPA.
