import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { SiteHeader } from '@/components/site-header'
import { Button } from '@/components/ui/button'
import {
  ApiError,
  joinWaitingRoom,
  waitingRoomStatus,
  type WaitingRoomStatus,
} from '@/api/client'
import { formatDateTime } from '@/lib/format'

export function WaitingRoomPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const performanceId = Number(id)
  const [status, setStatus] = useState<WaitingRoomStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    if (!Number.isInteger(performanceId) || performanceId <= 0) {
      setError('场次不存在。')
      setLoading(false)
      return
    }
    joinWaitingRoom(performanceId)
      .then((joined) => {
        if (active) {
          setStatus(joined)
          setLoading(false)
        }
      })
      .catch((caught) => {
        if (!active) {
          return
        }
        if (caught instanceof ApiError && caught.code === 'WAITING_ROOM_DISABLED') {
          navigate(`/performances/${performanceId}/seats`, { replace: true })
          return
        }
        setError(caught instanceof ApiError ? caught.message : '无法进入等待室，请稍后重试。')
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [performanceId, navigate])

  const polling = Boolean(status?.enabled && !status.admitted)

  useEffect(() => {
    if (!polling) {
      return
    }
    const timer = window.setInterval(() => {
      waitingRoomStatus(performanceId)
        .then(setStatus)
        .catch(() => undefined)
    }, 2000)
    return () => window.clearInterval(timer)
  }, [polling, performanceId])

  function enterSeats() {
    navigate(`/performances/${performanceId}/seats`)
  }

  return (
    <main className="min-h-svh bg-background text-foreground">
      <SiteHeader />

      <section className="mx-auto w-full max-w-2xl px-6 py-12">
        <Link
          to={status?.eventId ? `/events/${status.eventId}` : '/'}
          className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          ← 返回演出详情
        </Link>

        {loading ? (
          <div className="mt-10 animate-pulse space-y-3">
            <div className="h-7 w-1/2 rounded bg-secondary" />
            <div className="h-24 rounded bg-secondary" />
          </div>
        ) : error ? (
          <p role="alert" className="mt-10 text-sm text-destructive">
            {error}
          </p>
        ) : status ? (
          <div className="mt-8">
            <h1 className="text-2xl font-semibold tracking-tight">排队入场</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              {status.eventTitle ?? '演出'}
              {status.startsAt ? ` · ${formatDateTime(status.startsAt)}` : ''}
              {status.venueName ? ` · ${status.venueName}` : ''}
            </p>

            {!status.enabled ? (
              <section className="mt-8 border border-border bg-secondary/60 px-6 py-10 text-center">
                <p className="text-base font-medium">本场次无需排队。</p>
                <p className="mt-2 text-sm text-muted-foreground">可以直接进入购票页面。</p>
                <Button className="mt-6" onClick={enterSeats}>
                  进入购票
                </Button>
              </section>
            ) : status.admitted ? (
              <section className="mt-8 border border-emerald-700/25 bg-emerald-700/5 px-6 py-10 text-center">
                <p className="text-base font-medium text-emerald-800">已获得入场资格</p>
                <p className="mt-2 text-sm text-muted-foreground">
                  资格有效期至 {status.tokenExpiresAt ? formatDateTime(status.tokenExpiresAt) : '—'}，请尽快进入选座。
                </p>
                <Button className="mt-6" onClick={enterSeats}>
                  进入选座
                </Button>
              </section>
            ) : (
              <section className="mt-8 border border-border px-6 py-10">
                <p className="text-base font-medium">你正在排队</p>
                <div className="mt-6 grid gap-6 sm:grid-cols-2">
                  <div>
                    <p className="text-xs text-muted-foreground">当前位次</p>
                    <p className="mt-1 text-3xl font-semibold tracking-tight">
                      第 {status.position} 位
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">预计等待</p>
                    <p className="mt-1 text-3xl font-semibold tracking-tight">
                      {formatWait(status.estimatedWaitSeconds)}
                    </p>
                  </div>
                </div>
                <p className="mt-6 text-sm text-muted-foreground">
                  请保持页面打开，系统每 2 秒自动更新位次。
                </p>
              </section>
            )}
          </div>
        ) : null}
      </section>
    </main>
  )
}

function formatWait(seconds: number) {
  if (seconds < 60) {
    return `${seconds} 秒`
  }
  return `${Math.ceil(seconds / 60)} 分钟`
}
