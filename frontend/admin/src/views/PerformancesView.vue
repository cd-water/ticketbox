<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  ApiError,
  createPerformance,
  createPriceTier,
  getEvent,
  listPerformances,
  updatePerformance,
  updatePriceTier,
  type AdminEvent,
  type Performance,
  type PriceTier,
} from '@/lib/api'

const route = useRoute()
const router = useRouter()
const eventId = Number(route.params.eventId)

const event = ref<AdminEvent | null>(null)
const performances = ref<Performance[]>([])
const loading = ref(false)

const performanceDialog = ref(false)
const performanceSaving = ref(false)
const performanceRef = ref<FormInstance>()
const editingPerformanceId = ref<number | null>(null)
const performanceForm = reactive({
  startsAt: '',
  venueName: '',
  venueAddress: '',
  saleStartAt: '',
})

const performanceRules: FormRules = {
  startsAt: [{ required: true, message: '请选择开演时间', trigger: 'change' }],
  saleStartAt: [{ required: true, message: '请选择开票时间', trigger: 'change' }],
  venueName: [{ required: true, message: '请填写场馆', trigger: 'blur' }],
  venueAddress: [{ required: true, message: '请填写地址', trigger: 'blur' }],
}

const tierDialog = ref(false)
const tierSaving = ref(false)
const tierRef = ref<FormInstance>()
const editingTierId = ref<number | null>(null)
const tierPerformanceId = ref<number | null>(null)
const tierForm = reactive({
  name: '',
  price: 0,
  stock: 0,
})

const tierRules: FormRules = {
  name: [{ required: true, message: '请填写票档名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const [loadedEvent, loadedPerformances] = await Promise.all([
      getEvent(eventId),
      listPerformances(eventId),
    ])
    event.value = loadedEvent
    performances.value = loadedPerformances
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '加载场次失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!Number.isInteger(eventId) || eventId <= 0) {
    router.replace('/events')
    return
  }
  load()
})

function toIso(local: string) {
  return new Date(local).toISOString()
}

function toLocalInput(iso: string) {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
}

function resetPerformance() {
  editingPerformanceId.value = null
  performanceForm.startsAt = ''
  performanceForm.venueName = ''
  performanceForm.venueAddress = ''
  performanceForm.saleStartAt = ''
  performanceRef.value?.clearValidate()
}

function openCreatePerformance() {
  resetPerformance()
  performanceDialog.value = true
}

function openEditPerformance(performance: Performance) {
  editingPerformanceId.value = performance.id
  performanceForm.startsAt = toLocalInput(performance.startsAt)
  performanceForm.venueName = performance.venueName
  performanceForm.venueAddress = performance.venueAddress
  performanceForm.saleStartAt = toLocalInput(performance.saleStartAt)
  performanceDialog.value = true
}

async function savePerformance() {
  const valid = await performanceRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  performanceSaving.value = true
  try {
    const input = {
      startsAt: toIso(performanceForm.startsAt),
      venueName: performanceForm.venueName.trim(),
      venueAddress: performanceForm.venueAddress.trim(),
      saleStartAt: toIso(performanceForm.saleStartAt),
    }
    if (editingPerformanceId.value === null) {
      await createPerformance({ eventId, ...input })
      ElMessage.success('场次已创建')
    } else {
      await updatePerformance(editingPerformanceId.value, input)
      ElMessage.success('场次已更新')
    }
    performanceDialog.value = false
    await load()
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '保存场次失败')
  } finally {
    performanceSaving.value = false
  }
}

function resetTier() {
  editingTierId.value = null
  tierPerformanceId.value = null
  tierForm.name = ''
  tierForm.price = 0
  tierForm.stock = 0
  tierRef.value?.clearValidate()
}

function openCreateTier(performanceId: number) {
  resetTier()
  tierPerformanceId.value = performanceId
  tierDialog.value = true
}

function openEditTier(performanceId: number, tier: PriceTier) {
  editingTierId.value = tier.id
  tierPerformanceId.value = performanceId
  tierForm.name = tier.name
  tierForm.price = Number(tier.price)
  tierForm.stock = tier.stock
  tierDialog.value = true
}

async function saveTier() {
  const valid = await tierRef.value?.validate().catch(() => false)
  if (!valid || tierPerformanceId.value === null) {
    return
  }
  tierSaving.value = true
  try {
    const input = {
      name: tierForm.name.trim(),
      price: tierForm.price,
      stock: tierForm.stock,
    }
    if (editingTierId.value === null) {
      await createPriceTier({ performanceId: tierPerformanceId.value, ...input })
      ElMessage.success('票档已添加')
    } else {
      await updatePriceTier(editingTierId.value, input)
      ElMessage.success('票档已更新')
    }
    tierDialog.value = false
    await load()
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '保存票档失败')
  } finally {
    tierSaving.value = false
  }
}
</script>

<template>
  <section>
    <router-link to="/events" class="back">← 返回演出管理</router-link>

    <div class="page-head">
      <div>
        <h1>场次与票档</h1>
        <p>{{ event ? event.title : '加载中…' }}</p>
      </div>
      <el-button type="primary" @click="openCreatePerformance">新建场次</el-button>
    </div>

    <el-table v-loading="loading" :data="performances" row-key="id">
      <el-table-column type="expand">
        <template #default="{ row: performance }">
          <div class="tiers">
            <div class="tiers-head">
              <span class="tiers-title">票档</span>
              <el-button size="small" type="primary" text @click="openCreateTier(performance.id)">
                添加票档
              </el-button>
            </div>
            <el-table :data="performance.priceTiers" size="small">
              <el-table-column prop="name" label="名称" min-width="140" />
              <el-table-column label="价格" width="120">
                <template #default="{ row: tier }">¥{{ Number(tier.price).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="stock" label="库存" width="100" />
              <el-table-column label="操作" width="100">
                <template #default="{ row: tier }">
                  <el-button text type="primary" @click="openEditTier(performance.id, tier)">
                    编辑
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <span class="empty">还没有票档。</span>
              </template>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="开演时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.startsAt) }}</template>
      </el-table-column>
      <el-table-column prop="venueName" label="场馆" min-width="180" />
      <el-table-column label="开票时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.saleStartAt) }}</template>
      </el-table-column>
      <el-table-column label="票档数" width="90">
        <template #default="{ row }">{{ row.priceTiers.length }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEditPerformance(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有场次，先新建一场。" />
      </template>
    </el-table>

    <el-dialog
      v-model="performanceDialog"
      :title="editingPerformanceId === null ? '新建场次' : '编辑场次'"
      width="520px"
      @closed="resetPerformance"
    >
      <el-form ref="performanceRef" :model="performanceForm" :rules="performanceRules" label-position="top">
        <el-form-item label="开演时间" prop="startsAt">
          <el-input v-model="performanceForm.startsAt" type="datetime-local" />
        </el-form-item>
        <el-form-item label="开票时间" prop="saleStartAt">
          <el-input v-model="performanceForm.saleStartAt" type="datetime-local" />
        </el-form-item>
        <el-form-item label="场馆" prop="venueName">
          <el-input v-model="performanceForm.venueName" maxlength="120" placeholder="例如 梅赛德斯-奔驰文化中心" />
        </el-form-item>
        <el-form-item label="地址" prop="venueAddress">
          <el-input v-model="performanceForm.venueAddress" maxlength="200" placeholder="例如 浦东新区世博大道1200号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="performanceDialog = false">取消</el-button>
        <el-button type="primary" :loading="performanceSaving" @click="savePerformance">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="tierDialog"
      :title="editingTierId === null ? '添加票档' : '编辑票档'"
      width="440px"
      @closed="resetTier"
    >
      <el-form ref="tierRef" :model="tierForm" :rules="tierRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="tierForm.name" maxlength="64" placeholder="例如 内场 / 看台" />
        </el-form-item>
        <el-form-item label="价格（元）" prop="price">
          <el-input-number v-model="tierForm.price" :min="0" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="tierForm.stock" :min="0" :precision="0" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tierDialog = false">取消</el-button>
        <el-button type="primary" :loading="tierSaving" @click="saveTier">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.back {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-decoration: none;
}

.back:hover {
  color: var(--el-text-color-primary);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin: 16px 0 24px;
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

.tiers {
  padding: 8px 24px 20px 48px;
}

.tiers-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tiers-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
