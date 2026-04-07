import { useEffect, useState } from 'react'
import { apiGet } from '../api'

interface PendingOrder {
  id: number
  orderDate: string
  totalAmount: number
  items: string[]
  status: string
  createdAt: string
}

function statusClass(status: string): string {
  switch (status.toUpperCase()) {
    case 'PENDING':
      return 'cf-status cf-status-pending'
    case 'MATCHED':
      return 'cf-status cf-status-matched'
    case 'COMPLETED':
      return 'cf-status cf-status-completed'
    case 'DISCARDED':
      return 'cf-status cf-status-discarded'
    default:
      return 'cf-status cf-status-matched'
  }
}

export default function PendingOrdersView() {
  const [orders, setOrders] = useState<PendingOrder[]>([])

  useEffect(() => {
    apiGet<PendingOrder[]>('/api/orders/pending').then(setOrders)
  }, [])

  return (
    <div>
      <h1>Pending Orders</h1>
      <div className="cf-crt" data-testid="orders-table">
        {orders.length === 0 ? (
          <p className="cf-terminal-empty">No pending orders</p>
        ) : (
          <table className="cf-data-table">
            <thead>
              <tr>
                <th>Order Date</th>
                <th>Total</th>
                <th>Items</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id} data-testid="order-row">
                  <td>{order.orderDate}</td>
                  <td>{order.totalAmount.toFixed(2)}</td>
                  <td>{order.items.join(', ')}</td>
                  <td>
                    <span className={statusClass(order.status)}>{order.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
