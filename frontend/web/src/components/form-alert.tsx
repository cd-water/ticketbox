import type { ReactNode } from 'react'

export function FormAlert({
  tone = 'error',
  children,
}: {
  tone?: 'error' | 'status'
  children: ReactNode
}) {
  return (
    <p
      role={tone === 'status' ? 'status' : 'alert'}
      className={
        tone === 'status'
          ? 'mt-6 border border-border bg-secondary px-3 py-2 text-sm'
          : 'mt-6 border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive'
      }
    >
      {children}
    </p>
  )
}
