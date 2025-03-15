import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { ApiError, clearToken, me, type Profile } from '@/lib/api'

export function HomePage() {
  const navigate = useNavigate()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    me()
      .then((loaded) => {
        if (active) {
          setProfile(loaded)
        }
      })
      .catch((caught) => {
        if (!active) {
          return
        }
        if (caught instanceof ApiError && caught.status === 401) {
          clearToken()
          navigate('/login', { replace: true })
          return
        }
        setError('无法加载账号信息，请刷新重试。')
      })
    return () => {
      active = false
    }
  }, [navigate])

  function signOut() {
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
    <main className="min-h-svh bg-background text-foreground">
      <header className="border-b border-border">
        <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between px-6">
          <span className="text-sm font-semibold tracking-tight">TicketBox</span>
          <div className="flex items-center gap-4 text-sm">
            {profile && <span className="text-muted-foreground">{profile.username}</span>}
            <Button variant="ghost" size="sm" onClick={signOut}>
              退出登录
            </Button>
          </div>
        </div>
      </header>

      <section className="mx-auto w-full max-w-5xl px-6 py-16">
        {error ? (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        ) : profile ? (
          <div className="auth-enter">
            <h1 className="text-3xl font-semibold tracking-tight text-balance">
              欢迎回来，{profile.username}
            </h1>
            <p className="mt-3 max-w-xl text-base leading-relaxed text-muted-foreground">
              登录状态已建立。演出列表、选座与订单将在后续阶段接入这里。
            </p>

            <dl className="mt-10 grid max-w-md grid-cols-2 gap-6 border-t border-border pt-6 text-sm">
              <div>
                <dt className="text-muted-foreground">当前角色</dt>
                <dd className="mt-1 font-medium">
                  {profile.role === 'ADMIN' ? '管理员' : '购票用户'}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">会话</dt>
                <dd className="mt-1 font-medium">JWT · 24 小时</dd>
              </div>
            </dl>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">正在加载账号…</p>
        )}
      </section>
    </main>
  )
}
