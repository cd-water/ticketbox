import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { clearToken, getToken, me, type Profile } from '@/api/client'

export function SiteHeader() {
  const navigate = useNavigate()
  const [profile, setProfile] = useState<Profile | null>(null)

  useEffect(() => {
    if (!getToken()) {
      return
    }
    me()
      .then(setProfile)
      .catch(() => {
        clearToken()
        setProfile(null)
      })
  }, [])

  function signOut() {
    clearToken()
    setProfile(null)
    navigate('/')
  }

  return (
    <header className="border-b border-border">
      <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between px-6">
        <Link to="/" className="text-sm font-semibold tracking-tight">
          TicketBox
        </Link>
        <nav className="flex items-center gap-4 text-sm">
          {profile ? (
            <>
              <Link
                to="/orders"
                className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
              >
                我的订单
              </Link>
              <span className="text-muted-foreground">{profile.username}</span>
              <Button variant="ghost" size="sm" onClick={signOut}>
                退出登录
              </Button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
              >
                登录
              </Link>
              <Link
                to="/register"
                className="font-medium text-foreground underline underline-offset-4 hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
              >
                注册
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
