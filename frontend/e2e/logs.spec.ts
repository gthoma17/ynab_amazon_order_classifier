import { test, expect } from '@playwright/test'

test('shows empty state message when no logs', async ({ page }) => {
  await page.route('**/api/logs', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.goto('/logs')
  await expect(page.getByText('No logs')).toBeVisible()
})

test('shows sync logs in table', async ({ page }) => {
  await page.route('**/api/logs', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 1,
          source: 'EMAIL',
          lastRun: '2024-01-15T10:00:00Z',
          status: 'SUCCESS',
          message: null,
        },
      ]),
    })
  })

  await page.goto('/logs')
  await expect(page.getByText('EMAIL')).toBeVisible()
  await expect(page.getByText('SUCCESS')).toBeVisible()
})
