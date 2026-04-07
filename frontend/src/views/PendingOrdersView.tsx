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

export default function PendingOrdersView() {
  const [orders, setOrders] = useState<PendingOrder[]>([])

  useEffect(() => {
    apiGet<PendingOrder[]>('/api/orders/pending').then(setOrders)
  }, [])

  return (
    <div className="view">
      <h1>Pending Orders</h1>
      {orders.length === 0 ? (
        <div className="empty-state">
          <p className="empty-state__title">No pending orders</p>
          <p className="empty-state__body">
            No orders have been parsed yet. Orders appear here after the sync pipeline runs and
            finds Amazon order confirmation emails that have not yet been matched to a YNAB
            transaction.
          </p>
        </div>
      ) : (
        <div className="panel">
          <table>
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
                <tr key={order.id}>
                  <td>{order.orderDate}</td>
                  <td>{order.totalAmount.toFixed(2)}</td>
                  <td>{order.items.join(', ')}</td>
                  <td>{order.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
