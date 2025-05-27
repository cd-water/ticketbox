<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ApiError,
  getAdminOrder,
  listAdminOrders,
  type AdminOrderEntry,
  type AdminOrderItem,
  type AdminOrderSummary,
} from '@/lib/api'

const size = 20

const STATUS_PRESENTATION = {
  PENDING_PAYMENT: { label: '待支付', tag: 'warning' },
  PAID: { label: '已支付', tag: 'success' },
  CANCELLED: { label: '已取消', tag: 'info' },
} as const

const statusOptions = [
  { value: '', label: '全部状态' },
  ...Object.entries(STATUS_PRESENTATION).map(([value, presentation]) => ({
    value,
    label: presentation.label,
  })),
]

const entries = ref<AdminOrderSummary[]>([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminOrderEntry | null>(null)

async function load() {
  loading.value = true
  try {
    const result = await listAdminOrders({
      status: status.value || undefined,
      page: page.value - 1,
      size,
    })
    entries.value = result.items
    total.value = result.total
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '加载订单失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onStatusChange() {
  page.value = 1
  load()
}

function onPageChange(next: number) {
  page.value = next
  load()
}

async function openDetail(row: AdminOrderSummary) {
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await getAdminOrder(row.id)
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '加载订单详情失败')
  } finally {
    detailLoading.value = false
  }
}

function statusLabel(value: string) {
  return STATUS_PRESENTATION[value as keyof typeof STATUS_PRESENTATION]?.label ?? value
}

function statusTag(value: string) {
  return STATUS_PRESENTATION[value as keyof typeof STATUS_PRESENTATION]?.tag ?? 'info'
}

function formatDateTime(value: string | null) {
  return value
    ? new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
    : '—'
}

function itemLabel(item: AdminOrderItem) {
  if (item.seatId !== null) {
    return `${item.sectionName ?? ''} ${item.rowNo}排${item.seatNo}号`
  }
  return `${item.tierName ?? '无座'} ×${item.quantity}`
}
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>订单</h1>
        <p>查看全部订单、支付状态与电子票。</p>
      </div>
      <el-select v-model="status" placeholder="全部状态" style="width: 150px" @change="onStatusChange">
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="entries" :row-key="(row: AdminOrderSummary) => row.id">
      <el-table-column prop="orderNo" label="订单号" min-width="230" />
      <el-table-column prop="username" label="用户" width="140" />
      <el-table-column label="演出" min-width="200">
        <template #default="{ row }">{{ row.eventTitle ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" disable-transitions>
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ Number(row.totalAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="下单时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openDetail(row)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的订单。" />
      </template>
    </el-table>

    <div v-if="total > size" class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <el-dialog v-model="detailVisible" title="订单详情" width="640px">
      <div v-loading="detailLoading" class="detail-body">
        <div v-if="detail">
          <dl class="order-meta">
            <div>
              <dt>订单号</dt>
              <dd>{{ detail.order.orderNo }}</dd>
            </div>
            <div>
              <dt>用户</dt>
              <dd>{{ detail.username }}</dd>
            </div>
            <div>
              <dt>演出</dt>
              <dd>{{ detail.order.eventTitle ?? '—' }}</dd>
            </div>
            <div>
              <dt>场次</dt>
              <dd>
                {{ formatDateTime(detail.order.startsAt) }}
                <span v-if="detail.order.venueName"> · {{ detail.order.venueName }}</span>
              </dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ statusLabel(detail.order.status) }}</dd>
            </div>
            <div>
              <dt>金额</dt>
              <dd>¥{{ Number(detail.order.totalAmount).toFixed(2) }}</dd>
            </div>
          </dl>

          <h3>座位</h3>
          <el-table :data="detail.order.items" size="small">
            <el-table-column label="座位/票档" min-width="200">
              <template #default="{ row }">{{ itemLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="单价" width="110">
              <template #default="{ row }">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" />
          </el-table>

          <h3>电子票</h3>
          <el-table v-if="detail.order.tickets.length" :data="detail.order.tickets" size="small">
            <el-table-column prop="attendeeName" label="观演人" width="120" />
            <el-table-column label="座位/票档" min-width="180">
              <template #default="{ row }">
                {{ row.seatId !== null ? `${row.sectionName} ${row.rowNo}排${row.seatNo}号` : row.tierName }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ row.status === 'ISSUED' ? '待入场' : '已核销' }}</template>
            </el-table-column>
          </el-table>
          <p v-else class="muted">尚未出票。</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.page-head p {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-body {
  min-height: 120px;
}

.order-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin: 0 0 20px;
}

.order-meta dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.order-meta dd {
  margin: 2px 0 0;
  font-size: 14px;
}

h3 {
  margin: 20px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.muted {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
