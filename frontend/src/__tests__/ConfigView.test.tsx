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
  http.put('/api/config/keys', () => new HttpResponse(null, { status: 204 })),
  http.post('/api/config/probe/ynab', () =>
    HttpResponse.json({ success: true, message: 'Connected' })
  ),
  http.post('/api/config/probe/fastmail', () =>
    HttpResponse.json({ success: true, message: 'Connected' })
  ),
  http.post('/api/config/probe/gemini', () =>
    HttpResponse.json({ success: true, message: 'Connected' })
  )
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
    await screen.findByText('Saved')
  })

  // --- Test Connection buttons ---

  it('renders Test Connection buttons for each integration', () => {
    render(<ConfigView />)
    expect(screen.getByRole('button', { name: /test ynab/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /test fastmail/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /test gemini/i })).toBeInTheDocument()
  })

  it('disables YNAB test button when YNAB token is empty', async () => {
    server.use(
      http.get('/api/config/keys', () =>
        HttpResponse.json({
          ynabToken: null,
          ynabBudgetId: null,
          fastmailUser: null,
          fastmailToken: null,
          geminiKey: null,
        })
      )
    )
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/ynab token/i)).toHaveValue('')
    )
    expect(screen.getByRole('button', { name: /test ynab/i })).toBeDisabled()
  })

  it('disables FastMail test button when FastMail credentials are empty', async () => {
    server.use(
      http.get('/api/config/keys', () =>
        HttpResponse.json({
          ynabToken: null,
          ynabBudgetId: null,
          fastmailUser: null,
          fastmailToken: null,
          geminiKey: null,
        })
      )
    )
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/fastmail user/i)).toHaveValue('')
    )
    expect(screen.getByRole('button', { name: /test fastmail/i })).toBeDisabled()
  })

  it('disables Gemini test button when Gemini key is empty', async () => {
    render(<ConfigView />)
    // geminiKey is null from default server handler
    await waitFor(() =>
      expect(screen.getByLabelText(/gemini key/i)).toHaveValue('')
    )
    expect(screen.getByRole('button', { name: /test gemini/i })).toBeDisabled()
  })

  it('shows success result after YNAB test connection succeeds', async () => {
    const user = userEvent.setup()
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123')
    )

    await user.click(screen.getByRole('button', { name: /test ynab/i }))

    await waitFor(() =>
      expect(screen.getByLabelText('YNAB probe result')).toBeInTheDocument()
    )
    expect(screen.getByLabelText('YNAB probe result').textContent).toContain('Connected')
  })

  it('shows error result when YNAB test connection fails', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('/api/config/probe/ynab', () =>
        HttpResponse.json({ success: false, message: '401 Unauthorized — check your credentials' })
      )
    )

    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123')
    )

    await user.click(screen.getByRole('button', { name: /test ynab/i }))

    await waitFor(() =>
      expect(screen.getByLabelText('YNAB probe result')).toBeInTheDocument()
    )
    expect(screen.getByLabelText('YNAB probe result').textContent).toContain('401 Unauthorized')
  })

  it('clears probe results after save', async () => {
    const user = userEvent.setup()
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123')
    )

    // First run a probe to get a result
    await user.click(screen.getByRole('button', { name: /test ynab/i }))
    await waitFor(() =>
      expect(screen.getByLabelText('YNAB probe result')).toBeInTheDocument()
    )

    // Save should clear it
    await user.click(screen.getByRole('button', { name: /save/i }))
    await screen.findByText('Saved')
    expect(screen.queryByLabelText('YNAB probe result')).not.toBeInTheDocument()
  })
})
