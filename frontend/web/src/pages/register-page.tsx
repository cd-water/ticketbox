import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthLayout } from '@/components/auth-layout'
import { FormAlert } from '@/components/form-alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, register } from '@/lib/api'

interface FieldErrors {
  username?: string
  password?: string
  confirm?: string
}

export function RegisterPage() {
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(null)

    const errors: FieldErrors = {}
    if (username.trim().length < 3) {
      errors.username = '用户名至少 3 个字符。'
    }
    if (password.length < 6) {
      errors.password = '密码至少 6 个字符。'
    }
    if (confirm !== password) {
      errors.confirm = '两次输入的密码不一致。'
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      return
    }

    setSubmitting(true)
    try {
      await register(username.trim(), password)
      navigate('/login', { replace: true, state: { registered: true } })
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'USERNAME_TAKEN') {
        setFieldErrors({ username: '这个用户名已被使用，换一个试试。' })
      } else {
        setFormError(caught instanceof ApiError ? caught.message : '创建账号失败，请稍后重试')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <p className="text-sm font-semibold tracking-tight">TicketBox</p>
      <h1 className="mt-8 text-2xl font-semibold tracking-tight">创建账号</h1>
      <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
        注册后即可选座购票并查看电子票。
      </p>

      {formError && <FormAlert>{formError}</FormAlert>}

      <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
        <div className="space-y-2">
          <Label htmlFor="username">用户名</Label>
          <Input
            id="username"
            name="username"
            autoComplete="username"
            required
            aria-invalid={Boolean(fieldErrors.username)}
            aria-describedby={fieldErrors.username ? 'username-error' : undefined}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
          {fieldErrors.username && (
            <p id="username-error" className="text-sm text-destructive">
              {fieldErrors.username}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">密码</Label>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'password-error' : 'password-hint'}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {fieldErrors.password ? (
            <p id="password-error" className="text-sm text-destructive">
              {fieldErrors.password}
            </p>
          ) : (
            <p id="password-hint" className="text-sm text-muted-foreground">
              至少 6 个字符。
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="confirm">确认密码</Label>
          <Input
            id="confirm"
            name="confirm"
            type="password"
            autoComplete="new-password"
            required
            aria-invalid={Boolean(fieldErrors.confirm)}
            aria-describedby={fieldErrors.confirm ? 'confirm-error' : undefined}
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
          />
          {fieldErrors.confirm && (
            <p id="confirm-error" className="text-sm text-destructive">
              {fieldErrors.confirm}
            </p>
          )}
        </div>

        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? '创建中…' : '创建账号'}
        </Button>
      </form>

      <p className="mt-8 text-sm text-muted-foreground">
        已经有账号？{' '}
        <Link
          to="/login"
          className="font-medium text-foreground underline underline-offset-4 hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          去登录
        </Link>
      </p>
    </AuthLayout>
  )
}
