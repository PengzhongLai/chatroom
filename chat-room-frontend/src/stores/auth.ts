import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

interface UserInfo {
  id: number
  username: string
  nickname: string
  avatarUrl?: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(newToken: string, userInfo: UserInfo) {
    token.value = newToken
    user.value = userInfo
    localStorage.setItem('token', newToken)
  }

  async function fetchUser() {
  if (!token.value) return
  try {
    const { default: request } = await import('@/api/request')
    const res: any = await request.get('/users/me')
    user.value = res.data
  } catch { clearAuth() }
}

function clearAuth() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  return { token, user, isLoggedIn, setAuth, clearAuth, fetchUser }
})
