import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { OrderStatusBadge } from '@/components/order-status-badge'
import { SiteHeader } from '@/components/site-header'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  ApiError,
  cancelOrder,
  createPayment,
  getOrder,
  type AttendeeInput,
  type Order,
  type OrderItem,
} from '@/lib/api'
import { formatDateTime } from '@/lib/format'

export function OrderDetailPage() {
  const { id } = useParams()
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [cancelling, setCancelling] = useState(false)
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const [paying, setPaying] = useState(false)
  const [attendees, setAttendees] = useState<Record<number, { name: string; idNo: string }>>({})

  const seatItems: (OrderItem & { seatId: number })[] = order
    ? order.items.filter((item): item is OrderItem & { seatId: number } => item.seatId !== null)
    : []

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
        if (!active) {
          return
        }
        setOrder(loaded)
        setAttendees((current) => {
          const next = { ...current }
          for (const item of loaded.items) {
            if (item.seatId !== null && !next[item.seatId]) {
              next[item.seatId] = { name: '', idNo: '' }
            }
          }
          return next
        })
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

  async function submitPayment() {
    if (!order) {
      return
    }
    const list: AttendeeInput[] = []
    for (const item of seatItems) {
      const attendee = attendees[item.seatId]
      if (!attendee?.name.trim() || !attendee?.idNo.trim()) {
        setError('请填写每位观演人的姓名与证件号。')
        return
      }
      list.push({ seatId: item.seatId, name: attendee.name.trim(), idNo: attendee.idNo.trim() })
    }
    if (list.length === 0) {
      setError('订单没有可支付的座位。')
      return
    }

    setPaying(true)
    setError(null)
    try {
      await createPayment(order.id, list)
      for (let attempt = 0; attempt < 15; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 1000))
        const updated = await getOrder(order.id)
        setOrder(updated)
        if (updated.status === 'PAID') {
          return
        }
      }
      setError('支付处理中，请稍后刷新订单查看电子票。')
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '发起支付失败，请稍后重试。')
    } finally {
      setPaying(false)
    }
  }

  function updateAttendee(seatId: number, field: 'name' | 'idNo', value: string) {
    setAttendees((current) => ({
      ...current,
      [seatId]: { ...current[seatId], [field]: value },
    }))
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
                请在 {formatDateTime(order.expiresAt)} 前完成支付，超时订单将自动取消。
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
                  <li
                    key={item.seatId ?? item.tierName}
                    className="flex items-baseline justify-between gap-4 py-3 text-sm"
                  >
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
              <section className="mt-10 border-t border-border pt-6">
                <h2 className="text-lg font-semibold tracking-tight">观演人信息</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  每张票对应一位观演人，请填写姓名与证件号后再支付。
                </p>

                <div className="mt-5 space-y-5">
                  {seatItems.map((item) => (
                    <div key={item.seatId} className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
                      <div>
                        <p className="text-sm font-medium">
                          {item.sectionName} {item.rowNo}排{item.seatNo}号
                        </p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {item.tierName} · ¥{Number(item.unitPrice).toFixed(2)}
                        </p>
                      </div>
                      <div className="grid gap-3 sm:grid-cols-2">
                        <div className="space-y-1">
                          <Label htmlFor={`name-${item.seatId}`} className="text-xs">
                            姓名
                          </Label>
                          <Input
                            id={`name-${item.seatId}`}
                            value={attendees[item.seatId]?.name ?? ''}
                            maxLength={64}
                            onChange={(event) => updateAttendee(item.seatId, 'name', event.target.value)}
                          />
                        </div>
                        <div className="space-y-1">
                          <Label htmlFor={`id-${item.seatId}`} className="text-xs">
                            证件号
                          </Label>
                          <Input
                            id={`id-${item.seatId}`}
                            value={attendees[item.seatId]?.idNo ?? ''}
                            maxLength={64}
                            onChange={(event) => updateAttendee(item.seatId, 'idNo', event.target.value)}
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="mt-6 flex flex-wrap items-center gap-3">
                  <Button onClick={submitPayment} disabled={paying || cancelling}>
                    {paying ? '支付处理中…' : `支付 ¥${Number(order.totalAmount).toFixed(2)}`}
                  </Button>
                  <span className="text-xs text-muted-foreground">模拟支付，回调成功后自动出票。</span>
                </div>

                <div className="mt-8 border-t border-border pt-6">
                  {confirmingCancel ? (
                    <div className="flex flex-wrap items-center gap-3">
                      <span className="text-sm">确认取消这笔订单？座位会立即释放。</span>
                      <Button variant="destructive" size="sm" disabled={cancelling || paying} onClick={confirmCancel}>
                        {cancelling ? '取消中…' : '确认取消'}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setConfirmingCancel(false)}>
                        返回
                      </Button>
                    </div>
                  ) : (
                    <Button variant="outline" disabled={paying} onClick={() => setConfirmingCancel(true)}>
                      取消订单
                    </Button>
                  )}
                </div>
              </section>
            )}

            {order.status === 'PAID' && (
              <section className="mt-10 border-t border-border pt-6">
                <h2 className="text-xl font-semibold tracking-tight">电子票</h2>
                <p className="mt-1 text-sm text-muted-foreground">入场时出示二维码，一人一票。</p>

                {order.tickets.length === 0 ? (
                  <p className="mt-4 text-sm text-muted-foreground">电子票正在生成，请刷新页面。</p>
                ) : (
                  <div className="mt-6 grid gap-4 sm:grid-cols-2">
                    {order.tickets.map((ticket) => (
                      <div key={ticket.id} className="flex gap-4 border border-border bg-card p-4">
                        <QRCodeSVG
                          value={ticket.qrPayload}
                          size={112}
                          bgColor="#fffdf9"
                          fgColor="#1c1917"
                          level="M"
                        />
                        <div className="min-w-0">
                          <p className="text-sm font-medium">{ticket.attendeeName}</p>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {ticket.sectionName} {ticket.rowNo}排{ticket.seatNo}号
                          </p>
                          <p className="mt-3 text-xs text-muted-foreground">
                            {ticket.status === 'ISSUED' ? '待入场' : '已核销'}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>
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
