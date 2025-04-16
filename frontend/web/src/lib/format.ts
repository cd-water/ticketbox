const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Shanghai',
})

export function formatDateTime(value: string) {
  return dateTimeFormatter.format(new Date(value))
}
