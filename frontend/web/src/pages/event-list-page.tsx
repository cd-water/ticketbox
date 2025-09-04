import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { SiteHeader } from '@/components/site-header'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ApiError, listEvents, type EventSummary, type PageResult } from '@/api/client'

const PAGE_SIZE = 12

export function EventListPage() {
  const [cityInput, setCityInput] = useState('')
  const [city, setCity] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<PageResult<EventSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    listEvents({ city, page, size: PAGE_SIZE })
      .then((loaded) => {
        if (active) {
          setResult(loaded)
        }
      })
      .catch((caught) => {
        if (active) {
          setError(caught instanceof ApiError ? caught.message : '无法加载演出列表，请稍后重试。')
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
  }, [city, page])

  function applyCity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPage(0)
    setCity(cityInput.trim())
  }

  function clearCity() {
    setCityInput('')
    setCity('')
    setPage(0)
  }

  const totalPages = result ? Math.max(1, Math.ceil(result.total / PAGE_SIZE)) : 1

  return (
    <main className="min-h-svh bg-background text-foreground">
      <SiteHeader />

      <section className="mx-auto w-full max-w-5xl px-6 py-12">
        <div className="flex flex-col gap-6 border-b border-border pb-8 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight text-balance">演出</h1>
            <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
              按城市浏览正在售票的演出。
            </p>
          </div>
          <form className="flex w-full gap-2 sm:w-auto" onSubmit={applyCity}>
            <Input
              aria-label="按城市筛选"
              placeholder="城市，例如 上海"
              value={cityInput}
              onChange={(event) => setCityInput(event.target.value)}
              className="sm:w-48"
            />
            <Button type="submit" variant="secondary">
              筛选
            </Button>
            {city && (
              <Button type="button" variant="ghost" onClick={clearCity}>
                清除
              </Button>
            )}
          </form>
        </div>

        {error && (
          <p role="alert" className="mt-8 text-sm text-destructive">
            {error}
          </p>
        )}

        {loading ? (
          <div className="mt-10 grid gap-x-6 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="animate-pulse">
                <div className="aspect-[4/3] rounded-lg bg-secondary" />
                <div className="mt-3 h-4 w-2/3 rounded bg-secondary" />
                <div className="mt-2 h-3 w-1/3 rounded bg-secondary" />
              </div>
            ))}
          </div>
        ) : result && result.items.length > 0 ? (
          <>
            <div className="mt-10 grid gap-x-6 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
              {result.items.map((event) => (
                <EventCard key={event.id} event={event} />
              ))}
            </div>

            {totalPages > 1 && (
              <nav className="mt-14 flex items-center justify-between text-sm" aria-label="分页">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  上一页
                </Button>
                <span className="text-muted-foreground">
                  第 {page + 1} / {totalPages} 页
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  下一页
                </Button>
              </nav>
            )}
          </>
        ) : (
          <div className="mt-16 border border-dashed border-border px-6 py-16 text-center">
            <p className="text-base font-medium">
              {city ? `“${city}” 暂时没有演出。` : '暂时没有正在售票的演出。'}
            </p>
            <p className="mt-2 text-sm text-muted-foreground">
              {city ? '换一个城市看看，或清除筛选。' : '演出发布后会出现在这里。'}
            </p>
            {city && (
              <Button variant="outline" size="sm" className="mt-6" onClick={clearCity}>
                清除筛选
              </Button>
            )}
          </div>
        )}
      </section>
    </main>
  )
}

function EventCard({ event }: { event: EventSummary }) {
  return (
    <Link
      to={`/events/${event.id}`}
      className="group block focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
    >
      <div className="aspect-[4/3] overflow-hidden rounded-lg border border-border bg-secondary">
        {event.coverUrl ? (
          <img
            src={event.coverUrl}
            alt={`${event.title} 封面`}
            className="h-full w-full object-cover transition-transform duration-200 ease-out group-hover:scale-[1.02]"
            loading="lazy"
          />
        ) : (
          <div className="flex h-full items-center justify-center">
            <span className="text-5xl font-semibold tracking-tight text-primary/25">
              {event.title.slice(0, 1)}
            </span>
          </div>
        )}
      </div>
      <h2 className="mt-3 text-base font-medium tracking-tight transition-colors group-hover:text-primary">
        {event.title}
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">{event.city}</p>
    </Link>
  )
}
