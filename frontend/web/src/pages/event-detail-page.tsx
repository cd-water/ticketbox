import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { SiteHeader } from '@/components/site-header'
import { ApiError, getEvent, type EventDetail } from '@/lib/api'

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

            <p className="mt-10 border-t border-border pt-6 text-sm text-muted-foreground">
              场次与购票将在下一阶段开放。
            </p>
          </article>
        )}
      </section>
    </main>
  )
}
