<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import {
  ApiError,
  createEvent,
  listEvents,
  publishEvent,
  unpublishEvent,
  updateEvent,
  uploadFile,
  type AdminEvent,
} from '@/lib/api'

const size = 20
const router = useRouter()

const STATUS_PRESENTATION = {
  PUBLISHED: { label: '已发布', tag: 'success', action: '下架', actionType: 'warning' },
  DRAFT: { label: '草稿', tag: 'info', action: '发布', actionType: 'success' },
} as const

function presentation(status: AdminEvent['status']) {
  return STATUS_PRESENTATION[status]
}

const events = ref<AdminEvent[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const coverPreview = ref<string | null>(null)

const form = reactive({
  title: '',
  description: '',
  city: '',
  coverObjectKey: null as string | null,
})

const rules: FormRules = {
  title: [{ required: true, message: '请填写演出标题', trigger: 'blur' }],
  city: [{ required: true, message: '请填写城市', trigger: 'blur' }],
  description: [{ required: true, message: '请填写演出简介', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const result = await listEvents(page.value - 1, size)
    events.value = result.items
    total.value = result.total
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '加载演出失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function resetForm() {
  editingId.value = null
  form.title = ''
  form.description = ''
  form.city = ''
  form.coverObjectKey = null
  coverPreview.value = null
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openPerformances(event: AdminEvent) {
  router.push(`/events/${event.id}/performances`)
}

function openEdit(event: AdminEvent) {
  editingId.value = event.id
  form.title = event.title
  form.description = event.description
  form.city = event.city
  form.coverObjectKey = event.coverObjectKey
  coverPreview.value = event.coverUrl
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const input = { ...form, title: form.title.trim(), city: form.city.trim() }
    if (editingId.value === null) {
      await createEvent(input)
      ElMessage.success('演出已创建（草稿）')
    } else {
      await updateEvent(editingId.value, input)
      ElMessage.success('演出已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function togglePublish(event: AdminEvent) {
  try {
    if (event.status === 'PUBLISHED') {
      await unpublishEvent(event.id)
      ElMessage.success('演出已下架')
    } else {
      await publishEvent(event.id)
      ElMessage.success('演出已发布')
    }
    await load()
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '操作失败')
  }
}

async function handleUpload(options: UploadRequestOptions) {
  try {
    const result = await uploadFile(options.file)
    form.coverObjectKey = result.key
    coverPreview.value = URL.createObjectURL(options.file)
    ElMessage.success('封面已上传')
  } catch (caught) {
    ElMessage.error(caught instanceof ApiError ? caught.message : '上传失败')
    throw caught
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
}

function onPageChange(next: number) {
  page.value = next
  load()
}
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>演出管理</h1>
        <p>发布演出、上传封面，并控制用户端是否可见。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建演出</el-button>
    </div>

    <el-table v-loading="loading" :data="events" row-key="id" class="table">
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="city" label="城市" width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="presentation(row.status).tag" disable-transitions>
            {{ presentation(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openPerformances(row)">场次</el-button>
          <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text :type="presentation(row.status).actionType" @click="togglePublish(row)">
            {{ presentation(row.status).action }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有演出，先新建一场。" />
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建演出' : '编辑演出'"
      width="560px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" maxlength="64" placeholder="例如 上海" />
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="封面">
          <div class="cover-field">
            <el-upload
              :http-request="handleUpload"
              :show-file-list="false"
              accept="image/jpeg,image/png,image/webp"
            >
              <el-button>上传封面</el-button>
            </el-upload>
            <span class="cover-hint">JPEG / PNG / WebP，最大 5 MB</span>
          </div>
          <img v-if="coverPreview" :src="coverPreview" alt="封面预览" class="cover-preview" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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

.table {
  border-radius: 8px;
  overflow: hidden;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.cover-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cover-preview {
  width: 160px;
  aspect-ratio: 4 / 3;
  margin-top: 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  object-fit: cover;
}
</style>
