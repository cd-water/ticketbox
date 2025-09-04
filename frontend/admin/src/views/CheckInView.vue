<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
import { ElMessage } from 'element-plus'
import { ApiError, checkIn, type CheckInResult } from '@/api/client'

const payload = ref('')
const checking = ref(false)
const result = ref<CheckInResult | null>(null)
const error = ref<string | null>(null)
const scanning = ref(false)
const videoRef = ref<HTMLVideoElement | null>(null)

let controls: IScannerControls | null = null
let cameraRequest = 0
let scanHandled = false

async function submit(value: string) {
  const content = value.trim()
  if (!content) {
    ElMessage.warning('请先粘贴或扫描票面内容。')
    return
  }
  checking.value = true
  error.value = null
  result.value = null
  try {
    result.value = await checkIn(content)
    payload.value = ''
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '核销失败，请稍后重试。'
  } finally {
    checking.value = false
  }
}

async function toggleCamera() {
  if (scanning.value) {
    stopCamera()
    return
  }
  if (!videoRef.value) {
    return
  }
  const requestId = ++cameraRequest
  scanHandled = false
  try {
    const reader = new BrowserQRCodeReader()
    const scannerControls = await reader.decodeFromVideoDevice(undefined, videoRef.value, (scanResult) => {
      if (scanResult && !scanHandled) {
        scanHandled = true
        const text = scanResult.getText()
        stopCamera()
        payload.value = text
        submit(text)
      }
    })
    if (requestId !== cameraRequest) {
      scannerControls.stop()
      return
    }
    controls = scannerControls
    scanning.value = true
  } catch {
    if (requestId === cameraRequest) {
      ElMessage.error('无法开启摄像头，请改用粘贴方式。')
    }
  }
}

function stopCamera() {
  cameraRequest++
  controls?.stop()
  controls = null
  scanning.value = false
}

onBeforeUnmount(() => stopCamera())

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
}

function seatLabel(ticket: CheckInResult) {
  if (ticket.rowNo === null || ticket.seatNo === null) {
    return ticket.tierName ?? '无座'
  }
  return `${ticket.sectionName ?? ''} ${ticket.rowNo}排${ticket.seatNo}号`
}
</script>

<template>
  <section class="checkin">
    <div class="page-head">
      <h1>核销</h1>
      <p>扫描电子票二维码，或粘贴票面内容手动核销。每张票只能核销一次。</p>
    </div>

    <div class="panels">
      <section class="panel">
        <h2>摄像头扫码</h2>
        <div class="video-frame">
          <video ref="videoRef" muted playsinline aria-label="二维码扫描画面" />
          <p v-if="!scanning" class="video-hint">点击下方按钮开启摄像头。</p>
        </div>
        <el-button type="primary" :plain="!scanning" @click="toggleCamera">
          {{ scanning ? '停止扫码' : '开启摄像头扫码' }}
        </el-button>
      </section>

      <section class="panel">
        <h2>手动核销</h2>
        <el-form label-position="top" @submit.prevent="submit(payload)">
          <el-form-item label="票面内容或票号">
            <el-input
              v-model="payload"
              type="textarea"
              :rows="5"
              placeholder="粘贴二维码内容，或输入票号兜底"
            />
          </el-form-item>
          <el-button type="primary" :loading="checking" native-type="submit">核销</el-button>
        </el-form>
      </section>
    </div>

    <section v-if="result" class="result success">
      <div class="result-head">
        <span class="badge">核销成功</span>
        <span class="time">{{ formatDateTime(result.checkedInAt) }}</span>
      </div>
      <dl>
        <div>
          <dt>观演人</dt>
          <dd>{{ result.attendeeName }}</dd>
        </div>
        <div>
          <dt>证件号</dt>
          <dd>{{ result.attendeeIdNo }}</dd>
        </div>
        <div>
          <dt>座位</dt>
          <dd>{{ seatLabel(result) }}</dd>
        </div>
        <div>
          <dt>演出</dt>
          <dd>{{ result.eventTitle ?? '—' }}</dd>
        </div>
        <div>
          <dt>场次</dt>
          <dd>{{ formatDateTime(result.startsAt) }} · {{ result.venueName ?? '—' }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="error" class="result error">
      <span class="badge">核销失败</span>
      <p class="error-text">{{ error }}</p>
    </section>
  </section>
</template>

<style scoped>
.page-head h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.page-head p {
  margin: 6px 0 24px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

@media (max-width: 860px) {
  .panels {
    grid-template-columns: 1fr;
  }
}

.panel {
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.panel h2 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.video-frame {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 3;
  margin-bottom: 12px;
  overflow: hidden;
  border-radius: 6px;
  background: #1c1917;
}

.video-frame video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-hint {
  position: absolute;
  margin: 0;
  font-size: 13px;
  color: #d6d3d1;
}

.submit {
  margin-top: 12px;
}

.result {
  margin-top: 20px;
  padding: 16px 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.success .badge {
  color: #166534;
  background: #dcfce7;
}

.error .badge {
  color: #991b1b;
  background: #fee2e2;
}

.time {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.result dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin: 16px 0 0;
}

.result dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result dd {
  margin: 2px 0 0;
  font-size: 14px;
}

.error-text {
  margin: 10px 0 0;
  font-size: 14px;
  color: #991b1b;
}
</style>
