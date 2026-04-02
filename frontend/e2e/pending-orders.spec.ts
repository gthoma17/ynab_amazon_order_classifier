import { test, expect } from '@playwright/test'

test('shows empty state message when no orders', async ({ page }) => {
  await page.route('**/api/orders/pending', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.goto('/orders')
  await expect(page.getByText('No pending orders')).toBeVisible()
})

test('shows orders in table', async ({ page }) => {
  await page.route('**/api/orders/pending', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 1,
          orderDate: '2024-01-15T10:00:00Z',
          totalAmount: 49.99,
          items: ['USB Cable', 'Phone Case'],
          status: 'PENDING',
          createdAt: '2024-01-15T10:00:00Z',
        },
      ]),
    })
  })

  await page.goto('/orders')
  await expect(page.getByText('USB Cable, Phone Case')).toBeVisible()
})
