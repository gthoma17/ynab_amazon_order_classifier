import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/config/keys', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ynabToken: 'tok-123',
          ynabBudgetId: 'bgt-abc',
          fastmailUser: 'user@fm.com',
          fastmailToken: null,
          geminiKey: null,
        }),
      })
    } else {
      await route.fulfill({ status: 204 })
    }
  })
})

test('displays existing API key values', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('#ynabToken')).toHaveValue('tok-123')
  await expect(page.locator('#ynabBudgetId')).toHaveValue('bgt-abc')
  await expect(page.locator('#fastmailUser')).toHaveValue('user@fm.com')
  await expect(page.locator('#fastmailToken')).toHaveValue('')
  await expect(page.locator('#geminiKey')).toHaveValue('')
})

test('saves updated keys and shows Saved', async ({ page }) => {
  await page.goto('/')
  await page.locator('#ynabToken').fill('tok-updated')
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Saved')).toBeVisible()
})
