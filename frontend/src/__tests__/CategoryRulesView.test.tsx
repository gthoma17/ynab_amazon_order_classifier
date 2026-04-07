import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import CategoryRulesView from '../views/CategoryRulesView'

const ynabCategories = [
  { id: 'cat-1', name: 'Groceries', categoryGroupName: 'Food' },
  { id: 'cat-2', name: 'Electronics', categoryGroupName: 'Tech' },
]

const savedRules = [
  { id: 1, ynabCategoryId: 'cat-1', ynabCategoryName: 'Groceries', userDescription: 'Food items' },
]

const server = setupServer(
  http.get('/api/ynab/categories', () => HttpResponse.json(ynabCategories)),
  http.get('/api/config/categories', () => HttpResponse.json(savedRules)),
  http.put('/api/config/categories', () => new HttpResponse(null, { status: 204 })),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('CategoryRulesView', () => {
  it('renders a heading "Category Rules"', () => {
    render(<CategoryRulesView />)
    expect(screen.getByRole('heading', { name: /category rules/i })).toBeInTheDocument()
  })

  it('renders a row for each YNAB category', async () => {
    render(<CategoryRulesView />)
    await waitFor(() => {
      expect(screen.getByText('Groceries')).toBeInTheDocument()
      expect(screen.getByText('Electronics')).toBeInTheDocument()
    })
  })

  it('pre-fills user description from saved category rules', async () => {
    render(<CategoryRulesView />)
    await waitFor(() => {
      const inputs = screen.getAllByRole('textbox')
      const groceriesInput = inputs.find((el) => (el as HTMLInputElement).value === 'Food items')
      expect(groceriesInput).toBeInTheDocument()
    })
  })

  it('sends a PUT request with all category rules on save', async () => {
    const user = userEvent.setup()
    let capturedBody: unknown = null
    server.use(
      http.put('/api/config/categories', async ({ request }) => {
        capturedBody = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )

    render(<CategoryRulesView />)
    await waitFor(() => expect(screen.getByText('Electronics')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    const body = capturedBody as Array<{ ynabCategoryId: string }>
    expect(body.length).toBe(2)
    expect(body.map((r) => r.ynabCategoryId)).toContain('cat-1')
    expect(body.map((r) => r.ynabCategoryId)).toContain('cat-2')
  })

  it('shows a success message after save', async () => {
    const user = userEvent.setup()
    render(<CategoryRulesView />)
    await waitFor(() => expect(screen.getByText('Groceries')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /save/i }))
    await screen.findByText(/saved/i)
  })

  it('shows an empty state when no categories are returned', async () => {
    server.use(
      http.get('/api/ynab/categories', () => HttpResponse.json([])),
      http.get('/api/config/categories', () => HttpResponse.json([])),
    )
    render(<CategoryRulesView />)
    await waitFor(() => {
      expect(screen.getByText(/no categories loaded/i)).toBeInTheDocument()
    })
  })

  it('shows an error state when the categories fetch fails', async () => {
    server.use(http.get('/api/ynab/categories', () => new HttpResponse(null, { status: 500 })))
    render(<CategoryRulesView />)
    await waitFor(() => {
      expect(screen.getByText(/failed to load ynab categories/i)).toBeInTheDocument()
    })
  })
})
