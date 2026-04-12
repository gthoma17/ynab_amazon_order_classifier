import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import LogsView from '../views/LogsView'

const syncLogs = [
  {
    id: 1,
    source: 'EMAIL',
    lastRun: '2024-01-15T10:00:00Z',
    status: 'SUCCESS',
    message: null,
  },
  {
    id: 2,
    source: 'YNAB',
    lastRun: '2024-01-15T11:00:00Z',
    status: 'FAIL',
    message: 'Connection timeout',
  },
]

const server = setupServer(http.get('/api/logs', () => HttpResponse.json(syncLogs)))

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('LogsView', () => {
  it('renders a heading "Sync Logs"', () => {
    render(<LogsView />)
    expect(screen.getByRole('heading', { name: /sync logs/i })).toBeInTheDocument()
  })

  it('renders log rows loaded from the API', async () => {
    render(<LogsView />)
    await waitFor(() => {
      expect(screen.getByText('EMAIL')).toBeInTheDocument()
      expect(screen.getByText('YNAB')).toBeInTheDocument()
    })
  })

  it('shows the status of each log entry', async () => {
    render(<LogsView />)
    await waitFor(() => {
      expect(screen.getByText('SUCCESS')).toBeInTheDocument()
      expect(screen.getByText('FAIL')).toBeInTheDocument()
    })
  })

  it('shows error message for failed logs', async () => {
    render(<LogsView />)
    await waitFor(() => {
      expect(screen.getByText('Connection timeout')).toBeInTheDocument()
    })
  })

  it('shows an empty state message when there are no logs', async () => {
    server.use(http.get('/api/logs', () => HttpResponse.json([])))
    render(<LogsView />)
    await waitFor(() => {
      expect(screen.getByText(/no entries/i)).toBeInTheDocument()
    })
  })

  it('displays logs in reverse-chronological order (newest first)', async () => {
    render(<LogsView />)
    await waitFor(() => {
      expect(screen.getByText('EMAIL')).toBeInTheDocument()
      expect(screen.getByText('YNAB')).toBeInTheDocument()
    })
    const entries = screen.getAllByTestId('ser-entry')
    const texts = entries.map((e) => e.textContent ?? '')
    const ynabIndex = texts.findIndex((t) => t.includes('YNAB'))
    const emailIndex = texts.findIndex((t) => t.includes('EMAIL'))
    // YNAB log (11:00) is newer than EMAIL log (10:00) — must appear first
    expect(ynabIndex).toBeLessThan(emailIndex)
  })
})
