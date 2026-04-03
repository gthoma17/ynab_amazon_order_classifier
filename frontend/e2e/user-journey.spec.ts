import { test, expect } from '@playwright/test'

/**
 * User journey: first-time app setup and first sync cycle.
 *
 * This test exercises the complete stack:
 *   Browser → Vite proxy → real Spring Boot API → WireMock stubs for FastMail, YNAB, Gemini
 *
 * Steps:
 *  1. Open the app — API Keys page shows empty fields (fresh installation).
 *  2. Enter all five API credentials and save.
 *  3. Navigate to Category Rules — YNAB categories are fetched from the real backend
 *     (which calls the WireMock YNAB stub). Fill descriptions and save.
 *  4. Navigate to Pending Orders — wait for the email ingestion scheduler to run
 *     and populate the order, then assert it is visible.
 *  5. Navigate to Logs — assert the EMAIL sync log shows SUCCESS.
 *
 * The Spring Boot E2E server (started by Playwright's webServer config via
 * `./gradlew runE2EServer`) runs the real application with:
 *  - app.email.poll-interval-ms=3000  (scheduler fires every 3 s)
 *  - app.ynab.poll-interval-ms=3600000 (YNAB sync disabled during the test)
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

  // ── Step 3: Navigate to Category Rules, fill descriptions, save ─────────────
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

  // ── Step 4: Navigate to Pending Orders ─────────────────────────────────────
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

  // ── Step 5: Navigate to Logs — EMAIL sync shows SUCCESS ────────────────────
  // The scheduler may produce multiple log entries; assert that at least one
  // EMAIL/SUCCESS row is present.

  await page.getByRole('link', { name: 'Logs' }).click()
  await expect(page.getByRole('heading', { name: 'Sync Logs' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'EMAIL' }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'SUCCESS' }).first()).toBeVisible()
})
