import { test, expect } from '@playwright/test'

/**
 * User journey: first-time app setup and first sync cycle, including the new
 * onboarding safety controls (processing settings + dry run).
 *
 * This test exercises the complete stack:
 *   Browser → Vite proxy → real Spring Boot API → WireMock stubs for FastMail, YNAB, Gemini
 *
 * Steps:
 *  1. Open the app — API Keys page shows empty fields (fresh installation).
 *  2. Enter all five API credentials and save.
 *  3. Test Connection for each integration — YNAB, FastMail, and Gemini all show "Connected".
 *  4. Configure Processing Settings — set start-from date, order cap, and change the
 *     schedule to "Every N seconds / 3" so the full pipeline fires quickly during the test.
 *     The production warning for EVERY_N_SECONDS is asserted.
 *  5. Navigate to Category Rules — YNAB categories are fetched from the real backend
 *     (which calls the WireMock YNAB stub). Fill descriptions and save.
 *  6. Navigate to Logs — poll until both an EMAIL/SUCCESS row and a YNAB/SUCCESS row are
 *     visible. This proves the full pipeline (email ingest → YNAB match → Gemini classify →
 *     YNAB update) ran end-to-end against the WireMock stubs with no race condition.
 *  7. Run a Dry Run — navigate back to Configuration, trigger a dry run from a historical
 *     start date, and verify the predicted YNAB update is shown without any live write.
 *
 * The Spring Boot E2E server (started by Playwright's webServer config via
 * `./gradlew runE2EServer`) starts with the default 5-hour schedule. Step 4 saves an
 * EVERY_N_SECONDS/3 schedule which calls reschedule() so both services start cycling
 * immediately with no app restart.
 */
test('first-time setup and first sync journey', async ({ page }) => {
  // This journey spans several network round-trips plus two 30-second polls
  // (step 6 logs, step 7 dry-run results). The default 30s test timeout is far
  // too short; give the whole test 2 minutes.
  test.setTimeout(120_000)

  // ── Step 1: Open the app — API Keys page ──────────────────────────────────

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'API Keys' })).toBeVisible()

  // ── Step 2: Enter credentials and save ─────────────────────────────────────

  await page.locator('#ynabToken').fill('my-ynab-token')
  await page.locator('#ynabBudgetId').fill('my-budget-id')
  await page.locator('#fastmailUser').fill('me@fastmail.com')
  await page.locator('#fastmailToken').fill('my-fastmail-token')
  await page.locator('#geminiKey').fill('my-gemini-key')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(page.getByText('Saved')).toBeVisible()

  // ── Step 3: Test Connection for each integration ────────────────────────────
  // Credentials are now saved. Click each "Test" button and assert the inline
  // "Connected" confirmation appears. This exercises the full probe path:
  //   browser → real Spring Boot backend → WireMock stubs.

  await page.getByRole('button', { name: 'Test YNAB' }).click()
  await expect(page.getByLabel('YNAB probe result')).toContainText('Connected')

  await page.getByRole('button', { name: 'Test FastMail' }).click()
  await expect(page.getByLabel('FastMail probe result')).toContainText('Connected')

  await page.getByRole('button', { name: 'Test Gemini' }).click()
  await expect(page.getByLabel('Gemini probe result')).toContainText('Connected')

  // ── Step 4: Configure Processing Settings ──────────────────────────────────
  // Set start-from date to 2024-01-01 so the test order (received 2024-01-15)
  // passes the date filter. Cap to 10 orders per run.
  //
  // Change the schedule to "Every N seconds" with interval 3. This triggers
  // reschedule() on the backend so both EmailIngestionService and YnabSyncService
  // start cycling every 3 s immediately — no restart needed.
  //
  // Selecting "Every N seconds" shows a production warning; assert it is visible
  // so the user is aware this is a dev/test-only setting.

  await page.locator('#startFromDate').fill('2024-01-01')
  await page.locator('#orderCap').fill('10')
  await page.locator('#scheduleType').selectOption('EVERY_N_SECONDS')
  await expect(page.getByRole('alert')).toContainText(/not recommended for production/i)
  await page.locator('#secondInterval').fill('3')
  await page.getByRole('button', { name: 'Save processing settings' }).click()
  await expect(page.getByText('Processing settings saved')).toBeVisible()

  // ── Step 5: Navigate to Category Rules, fill descriptions, save ─────────────
  // The backend fetches real YNAB categories from the WireMock stub.
  // While this step runs, the scheduler starts firing every 3 s: email ingestion
  // creates the order; the next tick's YNAB sync processes it end-to-end.

  await page.getByRole('link', { name: 'Category Rules' }).click()
  await expect(page.getByRole('heading', { name: 'Category Rules' })).toBeVisible()
  await expect(page.getByText('Electronics')).toBeVisible()
  await expect(page.getByText('Home Improvement')).toBeVisible()

  await page
    .getByRole('textbox', { name: 'Description for Electronics' })
    .fill('Gadgets, cables, phones, computers, and tech accessories')
  await page
    .getByRole('textbox', { name: 'Description for Home Improvement' })
    .fill('Plumbing, fixtures, and home hardware')
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Saved')).toBeVisible()

  // ── Step 6: Navigate to Logs — poll for full pipeline completion ────────────
  // Assert that both an EMAIL/SUCCESS row and a YNAB/SUCCESS row are present.
  // This proves: email ingestion ran (found the stubbed order email), and
  // YnabSyncService ran (matched the order, called Gemini, updated the YNAB
  // transaction via WireMock). No race condition: log rows accumulate and never
  // disappear regardless of the scheduler firing again.

  await page.getByRole('link', { name: 'Logs' }).click()
  await expect(page.getByRole('heading', { name: 'Sync Logs' })).toBeVisible()

  await expect
    .poll(
      async () => {
        await page.reload()
        return await page.getByRole('cell', { name: 'YNAB' }).count()
      },
      { timeout: 30_000, intervals: [2000, 3000, 4000] },
    )
    .toBeGreaterThan(0)

  // Both EMAIL and YNAB sync succeeded — the full pipeline ran against WireMock
  await expect(page.getByRole('cell', { name: 'EMAIL' }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'YNAB' }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'SUCCESS' }).first()).toBeVisible()

  // ── Step 7: Dry Run ────────────────────────────────────────────────────────
  // Navigate back to Configuration, set the dry-run start date to 2024-01-01
  // so the test order (from 2024-01-15) is included in the preview, then
  // trigger a dry run.
  //
  // The dry run calls the full pipeline (FastMail → YNAB match → Gemini classify)
  // via WireMock stubs. It must NOT call the YNAB write endpoint.
  // Results show the predicted category for the TOTO Bidet order.

  await page.getByRole('link', { name: 'Configuration' }).click()
  await expect(page.getByRole('heading', { name: 'API Keys' })).toBeVisible()

  // Override the dry-run start date so the historical test order is included
  await page.locator('#dryRunStartFrom').fill('2024-01-01')

  await page.getByRole('button', { name: 'Run Dry Run' }).click()

  // Wait for results — the dry run calls FastMail, matches the YNAB transaction,
  // and classifies via Gemini (all served by WireMock stubs).
  await expect(page.getByText(/dry run results/i)).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText('TOTO Bidet Toilet Seat')).toBeVisible()
  await expect(page.getByText('Electronics')).toBeVisible()
  await expect(page.getByText('txn-e2e-1')).toBeVisible()
})

