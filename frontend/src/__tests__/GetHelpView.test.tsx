import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { vi } from 'vitest'
import GetHelpView from '../views/GetHelpView'

const server = setupServer(
  http.post('/api/help/report', () =>
    HttpResponse.json({
      body: '## Problem Description\n\nSomething broke\n',
      sanitized: false,
    })
  )
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
    expect(
      screen.getByRole('checkbox', { name: /include recent sync log entries/i })
    ).toBeChecked()
  })

  it('disables the Get Help button when description is empty', () => {
    render(<GetHelpView />)
    expect(screen.getByRole('button', { name: /get help/i })).toBeDisabled()
  })

  it('enables the Get Help button once description has text', async () => {
    const user = userEvent.setup()
    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    expect(screen.getByRole('button', { name: /get help/i })).not.toBeDisabled()
  })

  it('calls the report API and opens the GitHub new-issue URL on submit', async () => {
    const user = userEvent.setup()
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('button', { name: /get help/i }))

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledWith(
        expect.stringContaining(
          'github.com/gthoma17/budget-sortbot/issues/new'
        ),
        '_blank',
        'noopener,noreferrer'
      )
    })
  })

  it('shows a sanitized warning when the API reports sensitive values were removed', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'open').mockImplementation(() => null)
    server.use(
      http.post('/api/help/report', () =>
        HttpResponse.json({ body: 'Report with [REDACTED]', sanitized: true })
      )
    )

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'My token is secret')
    await user.click(screen.getByRole('button', { name: /get help/i }))

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(/sensitive values were removed/i)
    })
  })

  it('shows an error message when the API call fails', async () => {
    const user = userEvent.setup()
    server.use(http.post('/api/help/report', () => new HttpResponse(null, { status: 500 })))

    render(<GetHelpView />)
    await user.type(screen.getByLabelText(/describe the problem/i), 'Something broke')
    await user.click(screen.getByRole('button', { name: /get help/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })

  it('includes a note about no data being sent automatically', () => {
    render(<GetHelpView />)
    expect(screen.getByText(/you control final submission/i)).toBeInTheDocument()
  })
})
