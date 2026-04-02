import { test, expect } from '@playwright/test'

/**
 * User journey: first-time app setup and first sync cycle.
 *
 * Steps:
 *  1. Open the app — API keys page is empty (new installation).
 *  2. Enter all five API credentials and save.
 *  3. Navigate to Category Rules, load YNAB categories, fill descriptions and save.
 *  4. Navigate to Pending Orders — one order appears after the email sync ran.
 *  5. Navigate to Logs — a successful EMAIL sync log confirms the sync completed.
 */
test('first-time setup and first sync journey', async ({ page }) => {
  // ── Shared mutable state to simulate a real backend ────────────────────────

  let savedKeys: Record<string, string | null> = {
    ynabToken: null,
    ynabBudgetId: null,
    fastmailUser: null,
    fastmailToken: null,
    geminiKey: null,
  }

  let categoriesDescriptions: Record<string, string> = {}

  // ── Wire up API routes ──────────────────────────────────────────────────────

  await page.route('**/api/config/keys', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(savedKeys),
      })
    } else {
      const body = await route.request().postDataJSON()
      savedKeys = { ...savedKeys, ...body }
      await route.fulfill({ status: 204 })
    }
  })

  await page.route('**/api/ynab/categories', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 'cat-home', name: 'Home Improvement', categoryGroupName: 'Home' },
        { id: 'cat-tech', name: 'Electronics', categoryGroupName: 'Shopping' },
      ]),
    })
  })

  await page.route('**/api/config/categories', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          Object.entries(categoriesDescriptions).map(([id, desc]) => ({
            ynabCategoryId: id,
            ynabCategoryName: id,
            userDescription: desc,
          })),
        ),
      })
    } else {
      const rules = await route.request().postDataJSON()
      for (const rule of rules) {
        categoriesDescriptions[rule.ynabCategoryId] = rule.userDescription
      }
      await route.fulfill({ status: 204 })
    }
  })

  await page.route('**/api/orders/pending', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 1,
          orderDate: '2025-12-03T12:40:58Z',
          totalAmount: 426.0,
          items: ['TOTO® WASHLET® C2 Electronic Bidet Toilet Seat'],
          status: 'PENDING',
          createdAt: '2025-12-03T12:40:58Z',
        },
      ]),
    })
  })

  await page.route('**/api/logs', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 1,
          source: 'EMAIL',
          lastRun: '2025-12-03T12:41:00Z',
          status: 'SUCCESS',
          message: null,
        },
      ]),
    })
  })

  // ── Step 1: Open the app — API Keys page is empty ──────────────────────────

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'API Keys' })).toBeVisible()
  await expect(page.locator('#ynabToken')).toHaveValue('')
  await expect(page.locator('#geminiKey')).toHaveValue('')

  // ── Step 2: Enter credentials and save ─────────────────────────────────────

  await page.locator('#ynabToken').fill('my-ynab-token')
  await page.locator('#ynabBudgetId').fill('my-budget-id')
  await page.locator('#fastmailUser').fill('me@fastmail.com')
  await page.locator('#fastmailToken').fill('my-fastmail-token')
  await page.locator('#geminiKey').fill('my-gemini-key')
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Saved')).toBeVisible()

  // ── Step 3: Navigate to Category Rules, fill descriptions, save ─────────────

  await page.getByRole('link', { name: 'Category Rules' }).click()
  await expect(page.getByRole('heading', { name: 'Category Rules' })).toBeVisible()
  await expect(page.getByText('Home Improvement')).toBeVisible()
  await expect(page.getByText('Electronics')).toBeVisible()

  await page
    .getByRole('textbox', { name: 'Description for Home Improvement' })
    .fill('Plumbing, fixtures, and home hardware')
  await page
    .getByRole('textbox', { name: 'Description for Electronics' })
    .fill('Gadgets and tech accessories')
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Saved')).toBeVisible()

  // Verify descriptions were persisted in our mock state
  expect(categoriesDescriptions['cat-home']).toBe('Plumbing, fixtures, and home hardware')
  expect(categoriesDescriptions['cat-tech']).toBe('Gadgets and tech accessories')

  // ── Step 4: Navigate to Pending Orders — order from first sync is visible ──

  await page.getByRole('link', { name: 'Pending Orders' }).click()
  await expect(page.getByRole('heading', { name: 'Pending Orders' })).toBeVisible()
  await expect(
    page.getByText('TOTO® WASHLET® C2 Electronic Bidet Toilet Seat'),
  ).toBeVisible()
  await expect(page.getByText('426.00')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'PENDING' })).toBeVisible()

  // ── Step 5: Navigate to Logs — EMAIL sync shows SUCCESS ────────────────────

  await page.getByRole('link', { name: 'Logs' }).click()
  await expect(page.getByRole('heading', { name: 'Sync Logs' })).toBeVisible()
  await expect(page.getByText('EMAIL')).toBeVisible()
  await expect(page.getByText('SUCCESS')).toBeVisible()
})
