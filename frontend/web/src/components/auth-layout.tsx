import type { ReactNode } from 'react'

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="min-h-svh bg-background text-foreground">
      <div className="mx-auto grid min-h-svh w-full max-w-6xl lg:grid-cols-[minmax(0,1fr)_minmax(0,0.92fr)]">
        <section className="auth-enter flex items-center px-6 py-12 sm:px-10 lg:px-16">
          <div className="mx-auto w-full max-w-sm">{children}</div>
        </section>

        <aside className="relative hidden flex-col justify-center gap-10 border-l border-dashed border-border bg-secondary/60 px-14 py-16 lg:flex">
          <div className="max-w-md">
            <h2 className="text-3xl leading-[1.15] font-semibold tracking-tight text-balance">
              演出开场前，先把座位定下来。
            </h2>
            <p className="mt-4 text-base leading-relaxed text-muted-foreground">
              选座、下单、支付、出票在同一条流程里完成。热点场次排队入场，库存不超卖。
            </p>
          </div>

          <ol className="space-y-3 text-sm">
            <li className="flex items-baseline gap-3">
              <span className="font-mono text-xs text-muted-foreground">01</span>
              <span>浏览演出与场次</span>
            </li>
            <li className="flex items-baseline gap-3">
              <span className="font-mono text-xs text-muted-foreground">02</span>
              <span>选座、填写观演人、下单</span>
            </li>
            <li className="flex items-baseline gap-3">
              <span className="font-mono text-xs text-muted-foreground">03</span>
              <span>支付后拿到电子票，扫码入场</span>
            </li>
          </ol>
        </aside>
      </div>
    </main>
  )
}
