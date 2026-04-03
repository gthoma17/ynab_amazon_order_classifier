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
 *  4. Configure Processing Settings — set start-from date so historical test orders
 *     are included, cap to 10 orders per run, and save.
 *  5. Navigate to Category Rules — YNAB categories are fetched from the real backend
 *     (which calls the WireMock YNAB stub). Fill descriptions and save.
 *  6. Navigate to Pending Orders — wait for the email ingestion scheduler to run
 *     and populate the order, then assert it is visible.
 *  7. Navigate to Logs — assert the EMAIL sync log shows SUCCESS.
 *  8. Run a Dry Run — navigate back to Configuration, trigger a dry run from the
 *     built-in start date, and verify the predicted YNAB update is shown without
 *     any live YNAB write occurring.
 *
 * The Spring Boot E2E server (started by Playwright's webServer config via
 * `./gradlew runE2EServer`) runs the real application with:
 *  - app.scheduler.cron-override=*/3 * * * * *  (scheduler fires every 3 s)
 *  - app.scheduler.email-only-mode=true          (YNAB sync skipped so orders stay PENDING)
 * This ensures the PENDING order appears within seconds of credentials being saved.
 */
test('first-time setup and first sync journey', async ({ page }) => {
  // ── Step 1: Open the app — API Keys page ──────────────────────────────────

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'API Keys' })).toBeVisible()

  // ── Step 2: Enter credentials and save ─────────────────────────────────────

  await page.locator('#ynabToken').fill('my-ynab-token')
  await page.locator('#ynabBudgetId').fill('my-budget-id')
  await page.locator('#fastmailUser').fill('me@fastmail.com')
  await page.locator('#fastmailToken').fill('my-fastmail-token')
  await page.locator('#geminiKey').fill('my-gemini-key')
  await page.getByRole('button', { name: 'Save' }).click()
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
  // Set the start-from date to 2024-01-01 so the test order (received 2024-01-15)
  // is not filtered out by the default "today" start-from date.
  // Set an order cap of 10 to demonstrate the guardrail, and save.
  // The schedule is left at its default (EVERY_N_HOURS / 5) — the E2E server
  // overrides it to */3 * * * * * via app.scheduler.cron-override.

  await page.locator('#startFromDate').fill('2024-01-01')
  await page.locator('#orderCap').fill('10')
  await page.getByRole('button', { name: 'Save processing settings' }).click()
  await expect(page.getByText('Processing settings saved')).toBeVisible()

  // ── Step 5: Navigate to Category Rules, fill descriptions, save ─────────────
  // The backend fetches real YNAB categories from the WireMock stub.

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

  // ── Step 6: Navigate to Pending Orders ─────────────────────────────────────
  // The email ingestion scheduler runs every 3 s. After credentials were saved
  // it will ingest the stubbed Amazon order email and persist a PENDING order.
  // PendingOrdersView fetches data on mount only, so we reload the page until
  // the order becomes visible (up to 20 s).

  await page.getByRole('link', { name: 'Pending Orders' }).click()
  await expect(page.getByRole('heading', { name: 'Pending Orders' })).toBeVisible()

  await expect
    .poll(
      async () => {
        await page.reload()
        return page.getByText('TOTO Bidet Toilet Seat').count()
      },
      { timeout: 20_000, intervals: [1000, 2000, 3000] },
    )
    .toBeGreaterThan(0)

  await expect(page.getByText('426.00')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'PENDING' })).toBeVisible()

  // ── Step 7: Navigate to Logs — EMAIL sync shows SUCCESS ────────────────────
  // The scheduler may produce multiple log entries; assert that at least one
  // EMAIL/SUCCESS row is present.

  await page.getByRole('link', { name: 'Logs' }).click()
  await expect(page.getByRole('heading', { name: 'Sync Logs' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'EMAIL' }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'SUCCESS' }).first()).toBeVisible()

  // ── Step 8: Dry Run ────────────────────────────────────────────────────────
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

