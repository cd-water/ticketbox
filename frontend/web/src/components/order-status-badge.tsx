import type { OrderStatus } from '@/lib/api'

const STATUS_PRESENTATION: Record<OrderStatus, { label: string; className: string }> = {
  PENDING_PAYMENT: {
    label: '待支付',
    className: 'border-primary/30 bg-primary/10 text-primary',
  },
  PAID: {
    label: '已支付',
    className: 'border-emerald-700/25 bg-emerald-700/10 text-emerald-800',
  },
  CANCELLED: {
    label: '已取消',
    className: 'border-border bg-muted text-muted-foreground',
  },
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const presentation = STATUS_PRESENTATION[status]
  return (
    <span className={`inline-flex border px-2 py-0.5 text-xs font-medium ${presentation.className}`}>
      {presentation.label}
    </span>
  )
}
