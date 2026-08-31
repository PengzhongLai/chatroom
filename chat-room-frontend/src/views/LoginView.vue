<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import request from '@/api/request'
import { MacInput, MacButton } from '@/components/ui'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    const res: any = await request.post('/auth/login', form.value)
    authStore.setAuth(res.data.token, res.data.user)
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-bg">
    <div class="auth-card">
      <div class="auth-icon blue">
        <span>💬</span>
      </div>
      <h1 class="auth-title">在线聊天室</h1>
      <p class="auth-subtitle">登录你的账号开始聊天</p>

      <form @submit.prevent="handleLogin">
        <MacInput v-model="form.username" placeholder="用户名" autocomplete="username" />
        <div style="margin-top: 10px">
          <MacInput v-model="form.password" type="password" placeholder="密码" autocomplete="current-password" />
        </div>
        <MacButton variant="primary" style="width: 100%; margin-top: 16px; padding: 11px; font-size: 14px" :loading="loading" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </MacButton>
      </form>

      <p class="auth-switch">
        没有账号？<router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-bg {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--bg-primary);
}
.auth-card {
  width: 380px;
  padding: 40px 32px;
  background: var(--bg-card);
  backdrop-filter: blur(var(--blur-lg));
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  text-align: center;
  box-shadow: var(--shadow-xl);
}
.auth-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.auth-icon.blue {
  background: linear-gradient(135deg, var(--blue), #64d2ff);
  box-shadow: 0 4px 12px rgba(10, 132, 255, 0.3);
}
.auth-icon.green {
  background: linear-gradient(135deg, var(--green), #63e6a0);
  box-shadow: 0 4px 12px rgba(48, 209, 88, 0.3);
}
.auth-title { font-size: 22px; font-weight: 700; color: var(--text-secondary); margin-bottom: 4px; }
.auth-subtitle { font-size: 13px; color: var(--text-tertiary); margin-bottom: 28px; }
.auth-form { display: flex; flex-direction: column; gap: 10px; }
.auth-switch { margin-top: 20px; font-size: 13px; color: var(--text-tertiary); }
.auth-switch a { color: var(--blue); text-decoration: none; font-weight: 500; }
</style>
