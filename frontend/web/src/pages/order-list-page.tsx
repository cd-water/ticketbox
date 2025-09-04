import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { OrderStatusBadge } from '@/components/order-status-badge'
import { SiteHeader } from '@/components/site-header'
import { ApiError, listOrders, type Order, type PageResult } from '@/api/client'
import { formatDateTime } from '@/lib/format'

export function OrderListPage() {
  const [orders, setOrders] = useState<PageResult<Order> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    listOrders()
      .then((loaded) => {
        if (active) {
          setOrders(loaded)
        }
      })
      .catch((caught) => {
        if (active) {
          setError(caught instanceof ApiError ? caught.message : '无法加载订单，请稍后重试。')
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [])

  return (
    <main className="min-h-svh bg-background text-foreground">
      <SiteHeader />

      <section className="mx-auto w-full max-w-3xl px-6 py-12">
        <h1 className="text-2xl font-semibold tracking-tight">我的订单</h1>

        {loading ? (
          <div className="mt-8 animate-pulse space-y-3">
            <div className="h-20 rounded bg-secondary" />
            <div className="h-20 rounded bg-secondary" />
          </div>
        ) : error ? (
          <p role="alert" className="mt-8 text-sm text-destructive">
            {error}
          </p>
        ) : !orders || orders.items.length === 0 ? (
          <div className="mt-12 border border-dashed border-border px-6 py-16 text-center">
            <p className="text-base font-medium">还没有订单。</p>
            <p className="mt-2 text-sm text-muted-foreground">选一场演出，挑好座位后下单。</p>
            <Link
              to="/"
              className="mt-6 inline-block text-sm font-medium text-primary underline-offset-4 hover:underline"
            >
              浏览演出
            </Link>
          </div>
        ) : (
          <ul className="mt-6 divide-y divide-border border-y border-border">
            {orders.items.map((order) => (
              <li key={order.id} className="py-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <Link
                      to={`/orders/${order.id}`}
                      className="text-base font-medium transition-colors hover:text-primary"
                    >
                      {order.eventTitle ?? '演出'}
                    </Link>
                    <p className="mt-1 text-sm text-muted-foreground">
                      {order.startsAt ? formatDateTime(order.startsAt) : '时间待定'}
                      {order.venueName ? ` · ${order.venueName}` : ''}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">订单号 {order.orderNo}</p>
                  </div>
                  <div className="text-right">
                    <OrderStatusBadge status={order.status} />
                    <p className="mt-2 text-sm font-medium">¥{Number(order.totalAmount).toFixed(2)}</p>
                    <p className="text-xs text-muted-foreground">
                      {order.items.reduce((sum, item) => sum + item.quantity, 0)} 张
                    </p>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
