import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { SiteHeader } from '@/components/site-header'
import { ApiError, getSeatMap, type SeatMap, type SeatView } from '@/lib/api'

export function SeatMapPage() {
  const { id } = useParams()
  const [seatMap, setSeatMap] = useState<SeatMap | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    const performanceId = Number(id)
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
        }
      })
      .catch((caught) => {
        if (active) {
          setError(
            caught instanceof ApiError && caught.status === 404
              ? '场次不存在或尚未发布。'
              : '无法加载座位图，请稍后重试。',
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
        ) : error || !seatMap ? (
          <p role="alert" className="mt-10 text-sm text-destructive">
            {error ?? '场次不存在或尚未发布。'}
          </p>
        ) : (
          <div className="mt-8">
            <h1 className="text-2xl font-semibold tracking-tight">座位图</h1>

            {seatMap.seatingMode === 'GENERAL_ADMISSION' ? (
              <>
                <p className="mt-2 text-sm text-muted-foreground">本场次为无座场次，按票档计数售票。</p>
                <ul className="mt-8 divide-y divide-border border-y border-border">
                  {seatMap.priceTiers.map((tier) => (
                    <li key={tier.id} className="flex items-baseline justify-between py-4 text-sm">
                      <span>{tier.name}</span>
                      <span className="flex items-baseline gap-4">
                        <span className="font-medium">¥{Number(tier.price).toFixed(2)}</span>
                        <span className="text-muted-foreground">
                          {tier.stock > 0 ? `余票 ${tier.stock}` : '售罄'}
                        </span>
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            ) : (
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
                              style={{ gridTemplateColumns: `repeat(${section.seatsPerRow}, 1.5rem)` }}
                            >
                              {row.seats.map((seat) => (
                                <span
                                  key={seat.id}
                                  role="img"
                                  aria-label={`${row.rowNo}排${seat.seatNo}号`}
                                  title={`${row.rowNo}排${seat.seatNo}号`}
                                  className="h-6 w-6 rounded-[3px] border border-border bg-secondary"
                                />
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </section>
                ))}
              </div>
            )}

            <p className="mt-12 border-t border-border pt-6 text-sm text-muted-foreground">
              在线选座与购票将在后续阶段开放。
            </p>
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
