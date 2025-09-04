import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AuthLayout } from '@/components/auth-layout'
import { FormAlert } from '@/components/form-alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, login, setToken } from '@/api/client'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const registered = Boolean((location.state as { registered?: boolean } | null)?.registered)
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = await login(username.trim(), password)
      setToken(result.token)
      navigate(from, { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <p className="text-sm font-semibold tracking-tight">TicketBox</p>
      <h1 className="mt-8 text-2xl font-semibold tracking-tight">登录</h1>
      <p className="mt-2 text-sm leading-relaxed text-muted-foreground">继续购买演出票。</p>

      {registered && <FormAlert tone="status">账号已创建，请登录。</FormAlert>}
      {error && <FormAlert>{error}</FormAlert>}

      <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
        <div className="space-y-2">
          <Label htmlFor="username">用户名</Label>
          <Input
            id="username"
            name="username"
            autoComplete="username"
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">密码</Label>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? '登录中…' : '登录'}
        </Button>
      </form>

      <p className="mt-8 text-sm text-muted-foreground">
        还没有账号？{' '}
        <Link
          to="/register"
          className="font-medium text-foreground underline underline-offset-4 hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          创建账号
        </Link>
      </p>
    </AuthLayout>
  )
}
