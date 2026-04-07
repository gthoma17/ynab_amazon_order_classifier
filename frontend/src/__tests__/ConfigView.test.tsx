import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import ConfigView from '../views/ConfigView'

const defaultProcessingConfig = {
  orderCap: 0,
  startFromDate: '2024-01-01',
  installedAt: '2024-01-01',
  scheduleConfig: { type: 'EVERY_N_HOURS', hourInterval: 5 },
}

const server = setupServer(
  http.get('/api/config/keys', () =>
    HttpResponse.json({
      ynabToken: 'tok-123',
      ynabBudgetId: 'budget-abc',
      fastmailApiToken: 'fmjt_test-token',
      geminiKey: null,
    }),
  ),
  http.put('/api/config/keys', () => new HttpResponse(null, { status: 204 })),
  http.get('/api/ynab/budgets', () =>
    HttpResponse.json([
      { id: 'budget-abc', name: 'My Main Budget' },
      { id: 'budget-xyz', name: 'Savings Budget' },
    ]),
  ),
  http.post('/api/config/probe/ynab', () =>
    HttpResponse.json({ success: true, message: 'Connected' }),
  ),
  http.post('/api/config/probe/fastmail', () =>
    HttpResponse.json({ success: true, message: 'Connected' }),
  ),
  http.post('/api/config/probe/gemini', () =>
    HttpResponse.json({ success: true, message: 'Connected' }),
  ),
  http.get('/api/config/processing', () => HttpResponse.json(defaultProcessingConfig)),
  http.put('/api/config/processing', () => new HttpResponse(null, { status: 204 })),
  http.get('/api/config/dry-run/results', () => HttpResponse.json([])),
  http.post('/api/config/dry-run', () => HttpResponse.json([])),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('ConfigView', () => {
  it('renders a heading "API Keys"', () => {
    render(<ConfigView />)
    expect(screen.getByRole('heading', { name: /api keys/i })).toBeInTheDocument()
  })

  it('renders input fields for all four keys', () => {
    render(<ConfigView />)
    expect(screen.getByLabelText(/ynab token/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^budget$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/fastmail api token/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/gemini key/i)).toBeInTheDocument()
  })

  it('loads existing key values from the API', async () => {
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123'))
    // budget select should show the saved budget once budgets are loaded
    await waitFor(() => expect(screen.getByLabelText(/^budget$/i)).toHaveValue('budget-abc'))
    expect(screen.getByLabelText(/fastmail api token/i)).toHaveValue('fmjt_test-token')
    expect(screen.getByLabelText(/gemini key/i)).toHaveValue('')
  })

  it('sends a PUT request with updated values on save', async () => {
    const user = userEvent.setup()
    let capturedBody: unknown = null
    server.use(
      http.put('/api/config/keys', async ({ request }) => {
        capturedBody = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )

    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123'))

    const tokenInput = screen.getByLabelText(/ynab token/i)
    await user.clear(tokenInput)
    await user.type(tokenInput, 'new-token')

    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect((capturedBody as Record<string, string>).ynabToken).toBe('new-token')
  })

  it('shows a success message after save', async () => {
    const user = userEvent.setup()
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123'))
    await user.click(screen.getByRole('button', { name: /^save$/i }))
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
          fastmailApiToken: null,
          geminiKey: null,
        }),
      ),
    )
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue(''))
    expect(screen.getByRole('button', { name: /test ynab/i })).toBeDisabled()
  })

  // --- Budget dropdown ---

  it('budget select is disabled when YNAB token is empty', async () => {
    server.use(
      http.get('/api/config/keys', () =>
        HttpResponse.json({
          ynabToken: null,
          ynabBudgetId: null,
          fastmailApiToken: null,
          geminiKey: null,
        }),
      ),
    )
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue(''))
    expect(screen.getByLabelText(/^budget$/i)).toBeDisabled()
  })

  it('budget select is enabled and shows budget names once loaded', async () => {
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/^budget$/i)).not.toBeDisabled())
    expect(screen.getByRole('option', { name: 'My Main Budget' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Savings Budget' })).toBeInTheDocument()
  })

  it('budget select shows loading indicator while budgets are being fetched', async () => {
    let resolveBudgets!: () => void
    server.use(
      http.get('/api/ynab/budgets', async () => {
        await new Promise<void>((res) => {
          resolveBudgets = res
        })
        return HttpResponse.json([{ id: 'budget-abc', name: 'My Main Budget' }])
      }),
    )
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText('budgets loading')).toBeInTheDocument())
    resolveBudgets()
  })

  it('budget select shows an error when budget fetch fails', async () => {
    server.use(http.get('/api/ynab/budgets', () => new HttpResponse(null, { status: 401 })))
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
  })

  it('budget select shows empty state when no budgets returned', async () => {
    server.use(http.get('/api/ynab/budgets', () => HttpResponse.json([])))
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /no budgets found/i })).toBeInTheDocument(),
    )
    expect(screen.getByLabelText(/^budget$/i)).toBeDisabled()
  })

  it('disables FastMail test button when FastMail API token is empty', async () => {
    server.use(
      http.get('/api/config/keys', () =>
        HttpResponse.json({
          ynabToken: null,
          ynabBudgetId: null,
          fastmailApiToken: null,
          geminiKey: null,
        }),
      ),
    )
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/fastmail api token/i)).toHaveValue(''))
    expect(screen.getByRole('button', { name: /test fastmail/i })).toBeDisabled()
  })

  it('disables Gemini test button when Gemini key is empty', async () => {
    render(<ConfigView />)
    // geminiKey is null from default server handler
    await waitFor(() => expect(screen.getByLabelText(/gemini key/i)).toHaveValue(''))
    expect(screen.getByRole('button', { name: /test gemini/i })).toBeDisabled()
  })

  it('shows success result after YNAB test connection succeeds', async () => {
    const user = userEvent.setup()
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123'))

    await user.click(screen.getByRole('button', { name: /test ynab/i }))

    await waitFor(() =>
      expect(screen.getByLabelText('YNAB probe result').textContent).toContain('Connected'),
    )
  })

  it('shows error result when YNAB test connection fails', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('/api/config/probe/ynab', () =>
        HttpResponse.json({ success: false, message: '401 Unauthorized — check your credentials' }),
      ),
    )

    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123'))

    await user.click(screen.getByRole('button', { name: /test ynab/i }))

    await waitFor(() =>
      expect(screen.getByLabelText('YNAB probe result').textContent).toContain('401 Unauthorized'),
    )
  })

  it('clears probe results after save', async () => {
    const user = userEvent.setup()
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/ynab token/i)).toHaveValue('tok-123'))

    // First run a probe to get a result
    await user.click(screen.getByRole('button', { name: /test ynab/i }))
    await waitFor(() =>
      expect(screen.getByLabelText('YNAB probe result').textContent).toContain('Connected'),
    )

    // Save should reset probe to idle (readout shows placeholder)
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    await screen.findByText('Saved')
    expect(screen.getByLabelText('YNAB probe result').textContent).toContain('STANDING BY')
  })

  // --- Processing settings ---

  it('renders the Processing Settings section', () => {
    render(<ConfigView />)
    expect(screen.getByRole('heading', { name: /processing settings/i })).toBeInTheDocument()
  })

  it('renders order cap input', () => {
    render(<ConfigView />)
    expect(screen.getByLabelText(/max orders per run/i)).toBeInTheDocument()
  })

  it('renders start from date input', () => {
    render(<ConfigView />)
    expect(screen.getByLabelText(/start from date/i)).toBeInTheDocument()
  })

  it('renders schedule frequency selector', () => {
    render(<ConfigView />)
    expect(screen.getByRole('radiogroup', { name: /frequency/i })).toBeInTheDocument()
  })

  it('loads processing config values from the API', async () => {
    server.use(
      http.get('/api/config/processing', () =>
        HttpResponse.json({
          orderCap: 5,
          startFromDate: '2024-06-01',
          installedAt: '2024-01-01',
          scheduleConfig: { type: 'DAILY', hour: 14, minute: 0 },
        }),
      ),
    )
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByLabelText(/max orders per run/i)).toHaveValue(5))
    expect(screen.getByLabelText(/start from date/i)).toHaveValue('2024-06-01')
  })

  it('shows hour and minute selectors for DAILY schedule', async () => {
    server.use(
      http.get('/api/config/processing', () =>
        HttpResponse.json({
          orderCap: 0,
          startFromDate: null,
          installedAt: null,
          scheduleConfig: { type: 'DAILY', hour: 9, minute: 30 },
        }),
      ),
    )
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByRole('radio', { name: /^daily$/i })).toBeChecked())
    expect(screen.getByLabelText(/^hour$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^minute$/i)).toBeInTheDocument()
  })

  it('shows day-of-week selector for WEEKLY schedule', async () => {
    server.use(
      http.get('/api/config/processing', () =>
        HttpResponse.json({
          orderCap: 0,
          startFromDate: null,
          installedAt: null,
          scheduleConfig: { type: 'WEEKLY', hour: 8, minute: 0, dayOfWeek: 'MON' },
        }),
      ),
    )
    render(<ConfigView />)
    await waitFor(() => expect(screen.getByRole('radio', { name: /^weekly$/i })).toBeChecked())
    expect(screen.getByLabelText(/day of week/i)).toBeInTheDocument()
  })

  it('shows second-interval input and production warning for EVERY_N_SECONDS schedule', async () => {
    server.use(
      http.get('/api/config/processing', () =>
        HttpResponse.json({
          orderCap: 0,
          startFromDate: null,
          installedAt: null,
          scheduleConfig: { type: 'EVERY_N_SECONDS', secondInterval: 3 },
        }),
      ),
    )
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByRole('radio', { name: /every n seconds/i })).toBeChecked(),
    )
    expect(screen.getByRole('spinbutton', { name: /every n seconds/i })).toHaveValue(3)
    expect(screen.getByRole('alert')).toHaveTextContent(/not recommended for production/i)
  })

  it('sends secondInterval in PUT body when EVERY_N_SECONDS is selected', async () => {
    const user = userEvent.setup()
    let capturedBody: unknown = null
    server.use(
      http.get('/api/config/processing', () =>
        HttpResponse.json({
          orderCap: 0,
          startFromDate: null,
          installedAt: null,
          scheduleConfig: { type: 'EVERY_N_SECONDS', secondInterval: 5 },
        }),
      ),
      http.put('/api/config/processing', async ({ request }) => {
        capturedBody = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    render(<ConfigView />)
    await waitFor(() =>
      expect(screen.getByRole('radio', { name: /every n seconds/i })).toBeChecked(),
    )

    await user.click(screen.getByRole('button', { name: /save processing settings/i }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    const body = capturedBody as Record<string, unknown>
    const sc = body.scheduleConfig as Record<string, unknown>
    expect(sc.type).toBe('EVERY_N_SECONDS')
    expect(sc.secondInterval).toBe(5)
  })

  it('saves processing settings via PUT', async () => {
    const user = userEvent.setup()
    let capturedBody: unknown = null
    server.use(
      http.put('/api/config/processing', async ({ request }) => {
        capturedBody = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    render(<ConfigView />)
    await waitFor(() => screen.getByLabelText(/max orders per run/i))

    await user.click(screen.getByRole('button', { name: /save processing settings/i }))

    await waitFor(() => expect(capturedBody).not.toBeNull())
    expect(screen.getByText(/processing settings saved/i)).toBeInTheDocument()
  })

  // --- Dry run ---

  it('renders the Dry Run section', () => {
    render(<ConfigView />)
    expect(screen.getByRole('heading', { name: /dry run/i })).toBeInTheDocument()
  })

  it('renders Run Dry Run button', () => {
    render(<ConfigView />)
    expect(screen.getByRole('button', { name: /run dry run/i })).toBeInTheDocument()
  })

  it('renders dry-run start from date input', () => {
    render(<ConfigView />)
    expect(screen.getByLabelText(/dry-run start from/i)).toBeInTheDocument()
  })

  it('shows loading state while dry run is in progress', async () => {
    const user = userEvent.setup()
    let resolveRun!: () => void
    server.use(
      http.post('/api/config/dry-run', async () => {
        await new Promise<void>((res) => {
          resolveRun = res
        })
        return HttpResponse.json([])
      }),
    )
    render(<ConfigView />)
    await waitFor(() => screen.getByRole('button', { name: /run dry run/i }))

    await user.click(screen.getByRole('button', { name: /run dry run/i }))

    expect(screen.getByRole('button', { name: /running…/i })).toBeDisabled()
    resolveRun()
  })

  it('shows results after dry run completes', async () => {
    const user = userEvent.setup()
    const mockResult = {
      id: 1,
      orderId: 10,
      orderDate: '2024-01-15T10:00:00Z',
      totalAmount: '49.99',
      items: ['Keyboard'],
      ynabTransactionId: 'txn-abc',
      proposedCategoryId: 'cat-tech',
      proposedCategoryName: 'Technology',
      errorMessage: null,
      runAt: '2024-01-20T00:00:00Z',
    }
    server.use(http.post('/api/config/dry-run', () => HttpResponse.json([mockResult])))
    render(<ConfigView />)
    await waitFor(() => screen.getByRole('button', { name: /run dry run/i }))

    await user.click(screen.getByRole('button', { name: /run dry run/i }))

    await waitFor(() => expect(screen.getByText(/dry run results/i)).toBeInTheDocument())
    expect(screen.getByText('txn-abc')).toBeInTheDocument()
    expect(screen.getByText('Technology')).toBeInTheDocument()
  })
})
