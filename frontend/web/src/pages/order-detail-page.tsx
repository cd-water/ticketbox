import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { OrderStatusBadge } from '@/components/order-status-badge'
import { SiteHeader } from '@/components/site-header'
import { Button } from '@/components/ui/button'
import { ApiError, cancelOrder, getOrder, type Order } from '@/lib/api'
import { formatDateTime } from '@/lib/format'

export function OrderDetailPage() {
  const { id } = useParams()
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [cancelling, setCancelling] = useState(false)
  const [confirmingCancel, setConfirmingCancel] = useState(false)

  useEffect(() => {
    let active = true
    const orderId = Number(id)
    if (!Number.isInteger(orderId) || orderId <= 0) {
      setError('订单不存在。')
      setLoading(false)
      return
    }
    setLoading(true)
    getOrder(orderId)
      .then((loaded) => {
        if (active) {
          setOrder(loaded)
        }
      })
      .catch((caught) => {
        if (active) {
          setError(
            caught instanceof ApiError && caught.status === 404
              ? '订单不存在或无权查看。'
              : '无法加载订单，请稍后重试。',
          )
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
  }, [id])

  async function confirmCancel() {
    if (!order) {
      return
    }
    setCancelling(true)
    setError(null)
    try {
      const updated = await cancelOrder(order.id)
      setOrder(updated)
      setConfirmingCancel(false)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '取消订单失败，请稍后重试。')
    } finally {
      setCancelling(false)
    }
  }

  return (
    <main className="min-h-svh bg-background text-foreground">
      <SiteHeader />

      <section className="mx-auto w-full max-w-3xl px-6 py-12">
        <Link
          to="/orders"
          className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          ← 返回我的订单
        </Link>

        {loading ? (
          <div className="mt-10 animate-pulse space-y-3">
            <div className="h-7 w-1/2 rounded bg-secondary" />
            <div className="h-28 rounded bg-secondary" />
          </div>
        ) : !order ? (
          <p role="alert" className="mt-10 text-sm text-destructive">
            {error ?? '订单不存在或无权查看。'}
          </p>
        ) : (
          <article className="mt-8">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <h1 className="text-2xl font-semibold tracking-tight text-balance">
                  {order.eventTitle ?? '演出'}
                </h1>
                <p className="mt-2 text-sm text-muted-foreground">
                  {order.startsAt ? formatDateTime(order.startsAt) : '时间待定'}
                  {order.venueName ? ` · ${order.venueName}` : ''}
                </p>
              </div>
              <OrderStatusBadge status={order.status} />
            </div>

            {order.status === 'PENDING_PAYMENT' && (
              <p className="mt-6 border border-primary/25 bg-primary/5 px-3 py-2 text-sm">
                请在 {formatDateTime(order.expiresAt)} 前完成支付，超时订单将自动取消。支付功能将在下一阶段接入。
              </p>
            )}

            {error && (
              <p role="alert" className="mt-6 text-sm text-destructive">
                {error}
              </p>
            )}

            <div className="mt-8 border-y border-border">
              <ul className="divide-y divide-border">
                {order.items.map((item) => (
                  <li key={item.seatId ?? item.tierName} className="flex items-baseline justify-between gap-4 py-3 text-sm">
                    <span>
                      {item.sectionName ?? '座位'} {item.rowNo ?? ''}排{item.seatNo ?? ''}号
                      <span className="ml-2 text-muted-foreground">{item.tierName}</span>
                    </span>
                    <span className="font-medium">¥{Number(item.unitPrice).toFixed(2)}</span>
                  </li>
                ))}
              </ul>
              <div className="flex items-baseline justify-between border-t border-border py-3 text-sm">
                <span className="text-muted-foreground">合计</span>
                <span className="text-base font-semibold">¥{Number(order.totalAmount).toFixed(2)}</span>
              </div>
            </div>

            <dl className="mt-6 grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">订单号</dt>
                <dd className="mt-0.5">{order.orderNo}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">下单时间</dt>
                <dd className="mt-0.5">{formatDateTime(order.createdAt)}</dd>
              </div>
            </dl>

            {order.status === 'PENDING_PAYMENT' && (
              <div className="mt-8">
                {confirmingCancel ? (
                  <div className="flex flex-wrap items-center gap-3">
                    <span className="text-sm">确认取消这笔订单？座位会立即释放。</span>
                    <Button variant="destructive" size="sm" disabled={cancelling} onClick={confirmCancel}>
                      {cancelling ? '取消中…' : '确认取消'}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setConfirmingCancel(false)}>
                      返回
                    </Button>
                  </div>
                ) : (
                  <Button variant="outline" onClick={() => setConfirmingCancel(true)}>
                    取消订单
                  </Button>
                )}
              </div>
            )}

            {order.status === 'CANCELLED' && (
              <p className="mt-8 text-sm text-muted-foreground">订单已取消，座位已释放。</p>
            )}
          </article>
        )}
      </section>
    </main>
  )
}
