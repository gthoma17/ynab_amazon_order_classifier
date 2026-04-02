import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/ynab/categories', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 'cat-1', name: 'Groceries', categoryGroupName: 'Food' },
        { id: 'cat-2', name: 'Electronics', categoryGroupName: 'Shopping' },
      ]),
    })
  })

  await page.route('**/api/config/categories', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 1,
            ynabCategoryId: 'cat-1',
            ynabCategoryName: 'Groceries',
            userDescription: 'Food and groceries',
          },
        ]),
      })
    } else {
      await route.fulfill({ status: 204 })
    }
  })
})

test('displays categories from API in table', async ({ page }) => {
  await page.goto('/categories')
  await expect(page.getByText('Groceries')).toBeVisible()
  await expect(page.getByText('Electronics')).toBeVisible()
})

test('shows existing descriptions in inputs', async ({ page }) => {
  await page.goto('/categories')
  await expect(
    page.getByRole('textbox', { name: 'Description for Groceries' }),
  ).toHaveValue('Food and groceries')
})

test('saves category rules and shows Saved', async ({ page }) => {
  await page.goto('/categories')
  await page
    .getByRole('textbox', { name: 'Description for Electronics' })
    .fill('Gadgets and tech')
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Saved')).toBeVisible()
})
