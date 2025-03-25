<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ApiError, login, setToken } from '@/lib/api'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const error = ref<string | null>(null)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  error.value = null
  try {
    const result = await login(form.username.trim(), form.password)
    if (result.role !== 'ADMIN') {
      error.value = '该账号不是管理员，无法进入管理端。'
      return
    }
    setToken(result.token)
    router.replace('/events')
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '登录失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <p class="brand">TicketBox <span>管理端</span></p>
      <h1>登录</h1>
      <p class="subtitle">管理演出、场次与订单。</p>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="alert" />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="submitting" class="submit">
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100svh;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--el-bg-color-page);
}

.login-panel {
  width: 100%;
  max-width: 360px;
  padding: 36px 32px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.brand {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.brand span {
  margin-left: 6px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

h1 {
  margin: 28px 0 4px;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.subtitle {
  margin: 0 0 24px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.alert {
  margin-bottom: 16px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}
</style>
