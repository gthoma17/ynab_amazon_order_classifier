import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { vi } from 'vitest'
import GetHelpView from '../views/GetHelpView'

const server = setupServer(
  http.get('/api/help/config-state', () => HttpResponse.json([])),
  http.post('/api/help/report', () =>
    HttpResponse.json({
      body: '## Problem Description\n\nSomething broke\n',
      sanitized: false,
      truncated: false,
    }),
  ),
)

beforeAll(() => server.listen())
afterEach(() => {
  server.resetHandlers()
  vi.restoreAllMocks()
})
afterAll(() => server.close())

describe('GetHelpView', () => {
  it('renders a heading "Get Help"', () => {
    render(<GetHelpView />)
    expect(screen.getByRole('heading', { name: /get help/i })).toBeInTheDocument()
  })

  it('renders a description textarea', () => {
    render(<GetHelpView />)
    expect(screen.getByLabelText(/describe the problem/i)).toBeInTheDocument()
  })

  it('renders the sync log checkbox checked by default', () => {
    render(<GetHelpView />)
    expect(screen.getByRole('checkbox', { name: /include recent sync log entries/i })).toBeChecked()
  })

  it('renders the app logs checkbox unchecked by default', () => {
    render(<GetHelpView />)
    expect(
      screen.getByRole('checkbox', { name: /include full application logs/i }),
    ).not.toBeChecked()
  })

  it('disables the Open Issue button when description is empty', () => {
    render(<GetHelpView />)
    expect(screen.getByRole('button', { name: /open issue/i })).toBeDisabled()
  })

  it('enables the Open Issue button once description has text and logs are inserted', async () => {
    const user = userEvent.setup()
    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('button', { name: /insert logs/i }))
    await waitFor(() => expect(screen.getByText('LOGS INSERTED')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /open issue/i })).not.toBeDisabled()
  })

  it('shows a prominent redaction notice', () => {
    render(<GetHelpView />)
    expect(screen.getByRole('note', { name: /redaction notice/i })).toBeInTheDocument()
    expect(screen.getByText(/sensitive values/i)).toBeInTheDocument()
  })

  it('shows Insert Logs button when sync logs checkbox is checked', async () => {
    render(<GetHelpView />)
    // Sync logs checkbox is checked by default
    expect(screen.getByRole('button', { name: /insert logs/i })).toBeInTheDocument()
  })

  it('disables Insert Logs button when neither logs checkbox is checked', async () => {
    const user = userEvent.setup()
    render(<GetHelpView />)
    await user.click(screen.getByRole('checkbox', { name: /include recent sync log entries/i }))
    expect(screen.getByRole('button', { name: /insert logs/i })).toBeDisabled()
  })

  it('disables Open Issue when logs are checked but not yet inserted', async () => {
    const user = userEvent.setup()
    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    // sync logs checkbox is checked by default — button should be disabled until logs are inserted
    expect(screen.getByRole('button', { name: /open issue/i })).toBeDisabled()
  })

  it('opens issue directly when Open Issue is clicked with no logs checkboxes checked', async () => {
    const user = userEvent.setup()
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

    render(<GetHelpView />)
    await user.click(screen.getByRole('checkbox', { name: /include recent sync log entries/i }))
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('button', { name: /open issue/i }))

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledWith(
        expect.stringContaining('github.com/gthoma17/budget-sortbot/issues/new'),
        '_blank',
        'noopener,noreferrer',
      )
    })
  })

  it('calls the report API and opens the GitHub URL after Insert Logs then Open Issue', async () => {
    const user = userEvent.setup()
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('button', { name: /insert logs/i }))
    await waitFor(() => expect(screen.getByText('LOGS INSERTED')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: /open issue/i }))

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledWith(
        expect.stringContaining('github.com/gthoma17/budget-sortbot/issues/new'),
        '_blank',
        'noopener,noreferrer',
      )
    })
  })

  it('shows preview after inserting logs', async () => {
    const user = userEvent.setup()
    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('button', { name: /insert logs/i }))
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: /report body preview/i })).toBeInTheDocument(),
    )
    expect(screen.getByRole('textbox', { name: /report body preview/i })).toHaveValue(
      '## Problem Description\n\nSomething broke\n',
    )
  })

  it('passes includeAppLogs true to the API when the app logs checkbox is checked', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'open').mockImplementation(() => null)

    let capturedBody: Record<string, unknown> | null = null
    server.use(
      http.post('/api/help/report', async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ body: 'Report body', sanitized: false, truncated: false })
      }),
    )

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('checkbox', { name: /include full application logs/i }))
    await user.click(screen.getByRole('button', { name: /insert logs/i }))

    await waitFor(() => {
      expect(capturedBody).not.toBeNull()
      expect(capturedBody!.includeAppLogs).toBe(true)
    })
  })

  it('shows a sanitized warning when the API reports sensitive values were removed', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'open').mockImplementation(() => null)
    server.use(
      http.post('/api/help/report', () =>
        HttpResponse.json({ body: 'Report with [REDACTED]', sanitized: true, truncated: false }),
      ),
    )

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'My token is secret')
    await user.click(screen.getByRole('button', { name: /insert logs/i }))

    await waitFor(() => {
      expect(screen.getByText('SENSITIVE VALUES REDACTED')).toBeInTheDocument()
    })
  })

  it('appends truncation note to the URL when the API returns truncated=true', async () => {
    const user = userEvent.setup()
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    server.use(
      http.post('/api/help/report', () =>
        HttpResponse.json({ body: 'Truncated body content', sanitized: false, truncated: true }),
      ),
    )

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    // Insert logs so truncated flag is received from the API
    await user.click(screen.getByRole('button', { name: /insert logs/i }))
    await waitFor(() => {
      expect(screen.getByText('LOGS INSERTED')).toBeInTheDocument()
    })
    await user.click(screen.getByRole('button', { name: /open issue/i }))

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledWith(
        expect.stringContaining(encodeURIComponent('Log content truncated')),
        '_blank',
        'noopener,noreferrer',
      )
    })
  })

  it('shows an error message when the API call fails', async () => {
    const user = userEvent.setup()
    server.use(http.post('/api/help/report', () => new HttpResponse(null, { status: 500 })))

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('button', { name: /insert logs/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })

  it('includes a note about no data being sent automatically', () => {
    render(<GetHelpView />)
    expect(screen.getByText(/you control final submission/i)).toBeInTheDocument()
  })

  it('shows config state panel with SENSITIVE VALUES REMOVED for credential keys', async () => {
    server.use(
      http.get('/api/help/config-state', () =>
        HttpResponse.json([
          { key: 'YNAB_TOKEN', displayValue: 'SENSITIVE VALUES REMOVED' },
          { key: 'YNAB_BUDGET_ID', displayValue: 'my-budget-id' },
          { key: 'FASTMAIL_API_TOKEN', displayValue: 'SENSITIVE VALUES REMOVED' },
          { key: 'GEMINI_KEY', displayValue: 'SENSITIVE VALUES REMOVED' },
          { key: 'ORDER_CAP', displayValue: '10' },
        ]),
      ),
    )

    render(<GetHelpView />)

    await waitFor(() => {
      expect(
        screen.getByRole('region', { name: /system configuration state/i }),
      ).toBeInTheDocument()
    })

    expect(screen.getByTestId('config-state-YNAB_TOKEN')).toHaveTextContent(
      'SENSITIVE VALUES REMOVED',
    )
    expect(screen.getByTestId('config-state-FASTMAIL_API_TOKEN')).toHaveTextContent(
      'SENSITIVE VALUES REMOVED',
    )
    expect(screen.getByTestId('config-state-GEMINI_KEY')).toHaveTextContent(
      'SENSITIVE VALUES REMOVED',
    )
    expect(screen.getByTestId('config-state-YNAB_BUDGET_ID')).toHaveTextContent('my-budget-id')
    expect(screen.getByTestId('config-state-ORDER_CAP')).toHaveTextContent('10')
  })
})
