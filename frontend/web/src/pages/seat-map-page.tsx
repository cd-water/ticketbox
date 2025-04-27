import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { SiteHeader } from '@/components/site-header'
import { Button } from '@/components/ui/button'
import {
  ApiError,
  createGaOrder,
  createOrder,
  getSeatMap,
  getToken,
  type SeatMap,
  type SeatView,
} from '@/lib/api'

const MAX_SEATS_PER_ORDER = 4

export function SeatMapPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [seatMap, setSeatMap] = useState<SeatMap | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [submitting, setSubmitting] = useState(false)
  const [gaQuantity, setGaQuantity] = useState<Record<number, number>>({})
  const [buying, setBuying] = useState(false)

  const performanceId = Number(id)
  const loggedIn = Boolean(getToken())
  const selectionKey = `ticketbox.selection.${performanceId}`

  function restoreSelection(loaded: SeatMap) {
    const saved = sessionStorage.getItem(selectionKey)
    if (!saved) {
      setSelected(new Set())
      return
    }
    sessionStorage.removeItem(selectionKey)
    try {
      const ids: number[] = JSON.parse(saved)
      const available = new Set<number>()
      for (const section of loaded.sections) {
        for (const seat of section.seats) {
          if (seat.status === 'AVAILABLE') {
            available.add(seat.id)
          }
        }
      }
      setSelected(new Set(ids.filter((seatId) => available.has(seatId))))
    } catch {
      setSelected(new Set())
    }
  }

  useEffect(() => {
    let active = true
    if (!Number.isInteger(performanceId) || performanceId <= 0) {
      setError('场次不存在或尚未发布。')
      setLoading(false)
      return
    }
    setLoading(true)
    getSeatMap(performanceId)
      .then((loaded) => {
        if (active) {
          setSeatMap(loaded)
          restoreSelection(loaded)
        }
      })
      .catch((caught) => {
        if (!active) {
          return
        }
        if (caught instanceof ApiError && caught.code === 'TOKEN_REQUIRED') {
          navigate(`/performances/${performanceId}/waiting-room`, { replace: true })
          return
        }
        setError(
          caught instanceof ApiError && caught.status === 404
            ? '场次不存在或尚未发布。'
            : '无法加载座位图，请稍后重试。',
        )
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [performanceId])

  const seatInfo = useMemo(() => {
    const info = new Map<number, { price: number; label: string }>()
    for (const section of seatMap?.sections ?? []) {
      for (const seat of section.seats) {
        info.set(seat.id, {
          price: Number(section.priceTier.price),
          label: `${section.name} ${seat.rowNo}排${seat.seatNo}号`,
        })
      }
    }
    return info
  }, [seatMap])

  const total = [...selected].reduce((sum, seatId) => sum + (seatInfo.get(seatId)?.price ?? 0), 0)

  function toggleSeat(seat: SeatView) {
    if (seat.status !== 'AVAILABLE') {
      return
    }
    setError(null)
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(seat.id)) {
        next.delete(seat.id)
        return next
      }
      if (next.size >= MAX_SEATS_PER_ORDER) {
        setError(`每单最多选择 ${MAX_SEATS_PER_ORDER} 个座位。`)
        return current
      }
      next.add(seat.id)
      return next
    })
  }

  async function buyGa(priceTierId: number, quantity: number) {
    if (!loggedIn) {
      navigate('/login', { state: { from: `/performances/${performanceId}/seats` } })
      return
    }
    setBuying(true)
    setError(null)
    try {
      const order = await createGaOrder(performanceId, priceTierId, quantity, crypto.randomUUID())
      navigate(`/orders/${order.id}`)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '提交订单失败，请稍后重试。')
      if (caught instanceof ApiError && caught.code === 'SOLD_OUT') {
        getSeatMap(performanceId).then(setSeatMap).catch(() => undefined)
      }
    } finally {
      setBuying(false)
    }
  }

  async function submit() {
    if (!loggedIn) {
      sessionStorage.setItem(selectionKey, JSON.stringify([...selected]))
      navigate('/login', { state: { from: `/performances/${performanceId}/seats` } })
      return
    }
    if (selected.size === 0) {
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const order = await createOrder(performanceId, [...selected], crypto.randomUUID())
      navigate(`/orders/${order.id}`)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '提交订单失败，请稍后重试。')
      if (caught instanceof ApiError && caught.code === 'SEAT_ALREADY_LOCKED') {
        setSelected(new Set())
        getSeatMap(performanceId).then(setSeatMap).catch(() => undefined)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-svh bg-background text-foreground">
      <SiteHeader />

      <section className="mx-auto w-full max-w-5xl px-6 py-12">
        <Link
          to={seatMap ? `/events/${seatMap.eventId}` : '/'}
          className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          ← 返回演出详情
        </Link>

        {loading ? (
          <div className="mt-10 animate-pulse space-y-3">
            <div className="h-6 w-1/3 rounded bg-secondary" />
            <div className="h-40 rounded bg-secondary" />
          </div>
        ) : error && !seatMap ? (
          <p role="alert" className="mt-10 text-sm text-destructive">
            {error}
          </p>
        ) : !seatMap ? (
          <p className="mt-10 text-sm text-muted-foreground">场次不存在或尚未发布。</p>
        ) : (
          <div className="mt-8">
            <h1 className="text-2xl font-semibold tracking-tight">座位图</h1>

            {seatMap.seatingMode === 'GENERAL_ADMISSION' ? (
              <>
                <p className="mt-2 text-sm text-muted-foreground">
                  本场次为无座场次，按票档计数售票，每人每场最多 4 张。
                </p>

                {error && (
                  <p role="alert" className="mt-4 text-sm text-destructive">
                    {error}
                  </p>
                )}

                {!loggedIn && (
                  <p className="mt-4 border border-border bg-secondary px-3 py-2 text-sm">
                    <Link
                      to="/login"
                      state={{ from: `/performances/${performanceId}/seats` }}
                      className="font-medium underline underline-offset-4"
                    >
                      登录
                    </Link>{' '}
                    后即可提交订单。
                  </p>
                )}

                <ul className="mt-8 divide-y divide-border border-y border-border">
                  {seatMap.priceTiers.map((tier) => {
                    const soldOut = tier.stock === 0
                    const max = Math.max(1, Math.min(4, tier.stock))
                    const quantity = Math.min(gaQuantity[tier.id] ?? 1, max)
                    return (
                      <li key={tier.id} className="flex flex-wrap items-center justify-between gap-4 py-4">
                        <div>
                          <p className="text-sm font-medium">{tier.name}</p>
                          <p className="mt-1 text-sm text-muted-foreground">
                            ¥{Number(tier.price).toFixed(2)} · {soldOut ? '售罄' : `余票 ${tier.stock}`}
                          </p>
                        </div>
                        <div className="flex items-center gap-3">
                          <div className="flex items-center border border-border">
                            <button
                              type="button"
                              aria-label={`减少 ${tier.name} 数量`}
                              disabled={soldOut || quantity <= 1}
                              onClick={() =>
                                setGaQuantity((current) => ({
                                  ...current,
                                  [tier.id]: Math.max(1, quantity - 1),
                                }))
                              }
                              className="h-8 w-8 text-sm text-muted-foreground transition-colors hover:text-foreground disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                            >
                              −
                            </button>
                            <span className="w-8 text-center text-sm">{quantity}</span>
                            <button
                              type="button"
                              aria-label={`增加 ${tier.name} 数量`}
                              disabled={soldOut || quantity >= max}
                              onClick={() =>
                                setGaQuantity((current) => ({
                                  ...current,
                                  [tier.id]: Math.min(max, quantity + 1),
                                }))
                              }
                              className="h-8 w-8 text-sm text-muted-foreground transition-colors hover:text-foreground disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                            >
                              +
                            </button>
                          </div>
                          <Button size="sm" disabled={soldOut || buying} onClick={() => buyGa(tier.id, quantity)}>
                            {buying ? '提交中…' : '购买'}
                          </Button>
                        </div>
                      </li>
                    )
                  })}
                </ul>

                <p className="mt-6 text-sm text-muted-foreground">
                  提交订单后请填写观演人并完成模拟支付。
                </p>
              </>
            ) : (
              <>
                {error && (
                  <p role="alert" className="mt-6 text-sm text-destructive">
                    {error}
                  </p>
                )}

                {!loggedIn && (
                  <p className="mt-4 border border-border bg-secondary px-3 py-2 text-sm">
                    <Link to="/login" state={{ from: `/performances/${performanceId}/seats` }} className="font-medium underline underline-offset-4">
                      登录
                    </Link>{' '}
                    后即可提交订单；已选座位会保留。
                  </p>
                )}

                <div className="mt-6 flex flex-wrap items-center gap-5 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    <span className="h-3.5 w-3.5 rounded-[2px] border border-border bg-secondary" />
                    可售
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="h-3.5 w-3.5 rounded-[2px] border border-dashed border-muted-foreground/40 bg-muted" />
                    已锁定
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="h-3.5 w-3.5 rounded-[2px] border border-border bg-foreground/15" />
                    已售
                  </span>
                </div>

                <div className="mt-6 space-y-12">
                  {seatMap.sections.map((section) => (
                    <section key={section.id}>
                      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border pb-3">
                        <h2 className="text-lg font-semibold tracking-tight">{section.name}</h2>
                        <p className="text-sm text-muted-foreground">
                          {section.priceTier.name} · ¥{Number(section.priceTier.price).toFixed(2)}
                        </p>
                      </div>

                      <div className="mt-6 overflow-x-auto pb-2">
                        <div className="min-w-max space-y-1.5">
                          {groupByRow(section.seats).map((row) => (
                            <div key={row.rowNo} className="flex items-center gap-2">
                              <span className="w-9 text-right text-xs text-muted-foreground">
                                {row.rowNo}排
                              </span>
                              <div
                                className="grid gap-1.5"
                                style={{ gridTemplateColumns: `repeat(${section.seatsPerRow}, 1.75rem)` }}
                              >
                                {row.seats.map((seat) => {
                                  const isSelected = selected.has(seat.id)
                                  const unavailable = seat.status !== 'AVAILABLE'
                                  const stateLabel =
                                    seat.status === 'SOLD' ? '已售' : seat.status === 'LOCKED' ? '已锁定' : ''
                                  const appearance =
                                    seat.status === 'SOLD'
                                      ? 'cursor-not-allowed border-border bg-foreground/15 text-foreground/30 line-through'
                                      : seat.status === 'LOCKED'
                                        ? 'cursor-not-allowed border-dashed border-muted-foreground/40 bg-muted text-muted-foreground/40'
                                        : isSelected
                                          ? 'border-primary bg-primary text-primary-foreground'
                                          : 'border-border bg-secondary hover:bg-accent'
                                  return (
                                    <button
                                      key={seat.id}
                                      type="button"
                                      disabled={unavailable || submitting}
                                      aria-pressed={isSelected}
                                      aria-label={`${row.rowNo}排${seat.seatNo}号${stateLabel ? `，${stateLabel}` : ''}`}
                                      title={`${row.rowNo}排${seat.seatNo}号${stateLabel ? ` · ${stateLabel}` : ''}`}
                                      onClick={() => toggleSeat(seat)}
                                      className={[
                                        'flex h-7 w-7 items-center justify-center rounded-[3px] border text-[10px] transition-colors',
                                        appearance,
                                      ].join(' ')}
                                    >
                                      {seat.seatNo}
                                    </button>
                                  )
                                })}
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </section>
                  ))}
                </div>

                <div className="sticky bottom-4 mt-10 flex flex-wrap items-center justify-between gap-4 border border-border bg-card px-4 py-3 shadow-sm">
                  <div className="text-sm">
                    <span className="font-medium">已选 {selected.size} / {MAX_SEATS_PER_ORDER} 张</span>
                    <span className="ml-4 text-muted-foreground">合计 ¥{total.toFixed(2)}</span>
                  </div>
                  <Button onClick={submit} disabled={selected.size === 0 || submitting}>
                    {submitting ? '提交中…' : '提交订单'}
                  </Button>
                </div>

                <p className="mt-6 text-sm text-muted-foreground">
                  提交订单后将保留座位 15 分钟；支付将在下一阶段接入。
                </p>
              </>
            )}
          </div>
        )}
      </section>
    </main>
  )
}

function groupByRow(seats: SeatView[]) {
  const rows = new Map<number, SeatView[]>()
  for (const seat of seats) {
    const row = rows.get(seat.rowNo) ?? []
    row.push(seat)
    rows.set(seat.rowNo, row)
  }
  return [...rows.entries()]
    .sort(([left], [right]) => left - right)
    .map(([rowNo, rowSeats]) => ({
      rowNo,
      seats: [...rowSeats].sort((left, right) => left.seatNo - right.seatNo),
    }))
}
