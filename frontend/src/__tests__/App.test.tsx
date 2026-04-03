import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import App from '../App'

const server = setupServer(
  http.get('/api/config/keys', () => HttpResponse.json({ ynabToken: null, ynabBudgetId: null, fastmailApiToken: null, geminiKey: null })),
  http.get('/api/ynab/categories', () => HttpResponse.json([])),
  http.get('/api/config/categories', () => HttpResponse.json([])),
  http.get('/api/orders/pending', () => HttpResponse.json([])),
  http.get('/api/logs', () => HttpResponse.json([])),
  http.post('/api/help/report', () => HttpResponse.json({ body: '', sanitized: false }))
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('App navigation', () => {
  it('renders navigation links for all five views', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    )
    expect(screen.getByRole('link', { name: /configuration/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /category rules/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /pending orders/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /logs/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /get help/i })).toBeInTheDocument()
  })

  it.each([
    ['/', /api keys/i],
    ['/categories', /category rules/i],
    ['/orders', /pending orders/i],
    ['/logs', /sync logs/i],
    ['/help', /get help/i],
  ])('renders correct view heading at %s', (path, headingRegex) => {
    render(
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    )
    expect(screen.getByRole('heading', { name: headingRegex as RegExp })).toBeInTheDocument()
  })
})
