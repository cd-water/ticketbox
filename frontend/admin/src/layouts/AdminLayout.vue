<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { clearToken, me, type Profile } from '@/lib/api'

const router = useRouter()
const profile = ref<Profile | null>(null)

onMounted(async () => {
  try {
    const loaded = await me()
    if (loaded.role !== 'ADMIN') {
      clearToken()
      router.replace('/login')
      return
    }
    profile.value = loaded
  } catch {
    clearToken()
    router.replace('/login')
  }
})

function signOut() {
  clearToken()
  router.replace('/login')
}
</script>

<template>
  <div class="shell">
    <header class="header">
      <router-link to="/events" class="brand">
        TicketBox <span>管理端</span>
      </router-link>
      <nav class="nav">
        <router-link to="/events">演出管理</router-link>
        <router-link to="/orders">订单</router-link>
        <router-link to="/check-in">核销</router-link>
      </nav>
      <div class="user">
        <span v-if="profile">{{ profile.username }}</span>
        <el-button text @click="signOut">退出登录</el-button>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100svh;
}

.header {
  display: flex;
  align-items: center;
  gap: 2rem;
  height: 60px;
  padding: 0 24px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.brand {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--el-text-color-primary);
  text-decoration: none;
}

.brand span {
  margin-left: 6px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.nav {
  display: flex;
  gap: 16px;
  font-size: 14px;
}

.nav a {
  color: var(--el-text-color-secondary);
  text-decoration: none;
}

.nav a.router-link-active {
  color: var(--el-color-primary);
}

.user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}
</style>
