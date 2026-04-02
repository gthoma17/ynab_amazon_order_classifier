import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import ConfigView from '../views/ConfigView'

const server = setupServer(
  http.get('/api/config/keys', () =>
    HttpResponse.json({
      ynabToken: 'tok-123',
      ynabBudgetId: 'budget-abc',
      fastmailUser: 'user@example.com',
      fastmailToken: null,
      geminiKey: null,
    })
  ),
  http.put('/api/config/keys', () => new HttpResponse(null, { status: 204 }))
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('ConfigView', () => {
  it('renders a heading "API Keys"', () => {
    render(<ConfigView />)
    expect(screen.getByRole('heading', { name: /api keys/i })).toBeInTheDocument()
  })

  it('renders input fields for all five keys', () => {
    render(<ConfigView />)
    expect(screen.getByLabelText(/ynab token/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/budget id/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/fastmail user/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/fastmail token/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/gemini key/i)).toBeInTheDocument()
  })

  it('loads existing key values from the API', async () => {
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123')
    )
    expect(screen.getByLabelText(/budget id/i)).toHaveValue('budget-abc')
    expect(screen.getByLabelText(/fastmail user/i)).toHaveValue('user@example.com')
    expect(screen.getByLabelText(/fastmail token/i)).toHaveValue('')
    expect(screen.getByLabelText(/gemini key/i)).toHaveValue('')
  })

  it('sends a PUT request with updated values on save', async () => {
    const user = userEvent.setup()
    let capturedBody: unknown = null
    server.use(
      http.put('/api/config/keys', async ({ request }) => {
        capturedBody = await request.json()
        return new HttpResponse(null, { status: 204 })
      })
    )

    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123')
    )

    const tokenInput = screen.getByLabelText(/ynab token/i)
    await user.clear(tokenInput)
    await user.type(tokenInput, 'new-token')

    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect((capturedBody as Record<string, string>).ynabToken).toBe('new-token')
  })

  it('shows a success message after save', async () => {
    const user = userEvent.setup()
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123')
    )
    await user.click(screen.getByRole('button', { name: /save/i }))
    await screen.findByText(/saved/i)
  })
})
