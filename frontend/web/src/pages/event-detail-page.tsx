import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { SiteHeader } from '@/components/site-header'
import { ApiError, getEvent, type EventDetail } from '@/lib/api'

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Shanghai',
})

function formatDateTime(value: string) {
  return dateTimeFormatter.format(new Date(value))
}

export function EventDetailPage() {
  const { id } = useParams()
  const [event, setEvent] = useState<EventDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    const eventId = Number(id)
    if (!Number.isInteger(eventId) || eventId <= 0) {
      setError('演出不存在或尚未发布。')
      setLoading(false)
      return
    }
    setLoading(true)
    getEvent(eventId)
      .then((loaded) => {
        if (active) {
          setEvent(loaded)
        }
      })
      .catch((caught) => {
        if (active) {
          setError(
            caught instanceof ApiError && caught.status === 404
              ? '演出不存在或尚未发布。'
              : '无法加载演出，请稍后重试。',
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

      <section className="mx-auto w-full max-w-3xl px-6 py-12">
        <Link
          to="/"
          className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          ← 返回演出列表
        </Link>

        {loading ? (
          <div className="mt-10 animate-pulse">
            <div className="aspect-[16/9] rounded-lg bg-secondary" />
            <div className="mt-6 h-7 w-1/2 rounded bg-secondary" />
            <div className="mt-3 h-4 w-1/4 rounded bg-secondary" />
          </div>
        ) : error || !event ? (
          <p role="alert" className="mt-10 text-sm text-destructive">
            {error ?? '演出不存在或尚未发布。'}
          </p>
        ) : (
          <article className="mt-10">
            <div className="aspect-[16/9] overflow-hidden rounded-lg border border-border bg-secondary">
              {event.coverUrl ? (
                <img src={event.coverUrl} alt={`${event.title} 封面`} className="h-full w-full object-cover" />
              ) : (
                <div className="flex h-full items-center justify-center">
                  <span className="text-7xl font-semibold tracking-tight text-primary/25">
                    {event.title.slice(0, 1)}
                  </span>
                </div>
              )}
            </div>

            <h1 className="mt-8 text-3xl font-semibold tracking-tight text-balance">{event.title}</h1>
            <p className="mt-2 text-sm text-muted-foreground">{event.city}</p>

            <p className="mt-8 max-w-2xl text-base leading-relaxed whitespace-pre-line text-foreground/90">
              {event.description}
            </p>

            <section className="mt-12 border-t border-border pt-8">
              <h2 className="text-xl font-semibold tracking-tight">场次</h2>
              {event.performances.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">暂未公布场次。</p>
              ) : (
                <ul className="mt-2 divide-y divide-border">
                  {event.performances.map((performance) => (
                    <li
                      key={performance.id}
                      className="grid gap-4 py-6 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]"
                    >
                      <div>
                        <p className="text-base font-medium">{formatDateTime(performance.startsAt)}</p>
                        <p className="mt-1 text-sm">{performance.venueName}</p>
                        <p className="mt-1 text-sm text-muted-foreground">{performance.venueAddress}</p>
                        <p className="mt-2 text-xs text-muted-foreground">
                          开票时间：{formatDateTime(performance.saleStartAt)}
                        </p>
                      </div>
                      <div className="flex flex-wrap content-start gap-2 sm:justify-end">
                        {performance.priceTiers.length === 0 ? (
                          <span className="text-sm text-muted-foreground">票档即将公布</span>
                        ) : (
                          performance.priceTiers.map((tier) => (
                            <span
                              key={tier.id}
                              className="inline-flex items-baseline gap-2 border border-border px-3 py-2 text-sm"
                            >
                              <span>{tier.name}</span>
                              <span className="font-medium">¥{Number(tier.price).toFixed(2)}</span>
                              <span className="text-xs text-muted-foreground">
                                {tier.stock > 0 ? `余票 ${tier.stock}` : '售罄'}
                              </span>
                            </span>
                          ))
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              )}

              <p className="mt-8 text-sm text-muted-foreground">在线购票将在后续阶段开放。</p>
            </section>
          </article>
        )}
      </section>
    </main>
  )
}
