<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ApiError,
  generateSection,
  listSections,
  type Performance,
  type SectionSummary,
} from '@/api/client'

const props = defineProps<{
  modelValue: boolean
  performance: Performance | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  updated: []
}>()

const sections = ref<SectionSummary[]>([])
const loading = ref(false)
const generating = ref(false)

const form = reactive({
  name: '',
  priceTierId: undefined as number | undefined,
  rowCount: 10,
  seatsPerRow: 20,
})

const tierOptions = computed(() => props.performance?.priceTiers ?? [])
const seatingMode = computed(() => (sections.value.length > 0 ? 'SEATED' : 'GENERAL_ADMISSION'))

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetForm()
      loadSections()
    }
  },
)

function resetForm() {
  form.name = ''
  form.priceTierId = undefined
  form.rowCount = 10
  form.seatsPerRow = 20
}

async function loadSections() {
  if (!props.performance) {
    return
  }
  sections.value = []
  loading.value = true
  try {
    sections.value = await listSections(props.performance.id)
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '加载区域失败')
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (!props.performance) {
    return
  }
  if (!form.name.trim() || form.priceTierId === undefined) {
    ElMessage.warning('请填写区域名称并选择票档')
    return
  }
  generating.value = true
  try {
    await generateSection(props.performance.id, {
      name: form.name.trim(),
      priceTierId: form.priceTierId,
      rowCount: form.rowCount,
      seatsPerRow: form.seatsPerRow,
    })
    ElMessage.success('区域已生成')
    form.name = ''
    await loadSections()
    emit('updated')
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '生成区域失败')
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="区域与座位"
    width="680px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="performance" class="body">
      <p class="hint">
        当前类型：{{ seatingMode === 'SEATED' ? '对号入座' : '无座' }}
        <span v-if="seatingMode !== 'SEATED'">（生成区域后转为对号入座）</span>
      </p>

      <el-table v-loading="loading" :data="sections" size="small">
        <el-table-column prop="name" label="区域" min-width="140" />
        <el-table-column label="票档" min-width="140">
          <template #default="{ row }">{{ row.priceTier.name }}</template>
        </el-table-column>
        <el-table-column prop="rowCount" label="排数" width="80" />
        <el-table-column prop="seatsPerRow" label="每排座位" width="100" />
        <el-table-column prop="seatCount" label="座位数" width="90" />
        <template #empty>
          <span class="empty">还没有区域。</span>
        </template>
      </el-table>

      <div class="generate">
        <h3>生成区域</h3>
        <p class="hint">一次生成一个规整区域：排数 × 每排座位数。</p>
        <el-form label-position="top" class="form" @submit.prevent="generate">
          <div class="grid">
            <el-form-item label="区域名称">
              <el-input v-model="form.name" maxlength="64" placeholder="例如 内场A区" />
            </el-form-item>
            <el-form-item label="票档">
              <el-select v-model="form.priceTierId" placeholder="选择票档" style="width: 100%">
                <el-option
                  v-for="tier in tierOptions"
                  :key="tier.id"
                  :label="`${tier.name} · ¥${Number(tier.price).toFixed(2)}`"
                  :value="tier.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="排数">
              <el-input-number v-model="form.rowCount" :min="1" :max="30" />
            </el-form-item>
            <el-form-item label="每排座位数">
              <el-input-number v-model="form.seatsPerRow" :min="1" :max="50" />
            </el-form-item>
          </div>
          <el-button type="primary" :loading="generating" native-type="submit">生成</el-button>
        </el-form>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.generate h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
}

.generate {
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}

.form {
  margin-top: 12px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
</style>
