import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import PendingOrdersView from '../views/PendingOrdersView'

const pendingOrders = [
  {
    id: 1,
    orderDate: '2024-01-15T10:00:00Z',
    totalAmount: 59.99,
    items: ['Book', 'Pen'],
    status: 'PENDING',
    createdAt: '2024-01-15T10:05:00Z',
  },
  {
    id: 2,
    orderDate: '2024-01-16T12:00:00Z',
    totalAmount: 129.0,
    items: ['Keyboard'],
    status: 'PENDING',
    createdAt: '2024-01-16T12:05:00Z',
  },
]

const server = setupServer(http.get('/api/orders/pending', () => HttpResponse.json(pendingOrders)))

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('PendingOrdersView', () => {
  it('renders a heading "Pending Orders"', () => {
    render(<PendingOrdersView />)
    expect(screen.getByRole('heading', { name: /pending orders/i })).toBeInTheDocument()
  })

  it('renders a table with order rows loaded from the API', async () => {
    render(<PendingOrdersView />)
    await waitFor(() => {
      expect(screen.getByText('59.99')).toBeInTheDocument()
      expect(screen.getByText('129.00')).toBeInTheDocument()
    })
  })

  it('shows item names in each row', async () => {
    render(<PendingOrdersView />)
    await waitFor(() => {
      expect(screen.getByText(/Book/)).toBeInTheDocument()
      expect(screen.getByText(/Keyboard/)).toBeInTheDocument()
    })
  })

  it('shows the status of each order', async () => {
    render(<PendingOrdersView />)
    await waitFor(() => {
      const statuses = screen.getAllByText('PENDING')
      expect(statuses.length).toBe(2)
    })
  })

  it('shows an empty state message when there are no pending orders', async () => {
    server.use(http.get('/api/orders/pending', () => HttpResponse.json([])))
    render(<PendingOrdersView />)
    await waitFor(() => {
      expect(screen.getByText(/no pending orders/i)).toBeInTheDocument()
    })
  })
})
