import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

interface UserInfo {
  id: number
  username: string
  nickname: string
  avatarUrl?: string
}

// 登录状态仓库。token 同时存在内存 ref 和 localStorage：
// 内存用于组件响应式读取，localStorage 用于刷新页面后恢复登录
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<UserInfo | null>(null)

  // 只判断"本地有没有 token"，不代表 token 仍有效（可能已过期或用户已删除）
  const isLoggedIn = computed(() => !!token.value)

  /** 登录成功后写入 token 和用户信息 */
  function setAuth(newToken: string, userInfo: UserInfo) {
    token.value = newToken
    user.value = userInfo
    localStorage.setItem('token', newToken)
  }

  // 刷新页面后用它恢复用户信息；请求失败说明 token 已失效，直接清空登录状态
  async function fetchUser() {
  if (!token.value) return
  try {
    const { default: request } = await import('@/api/request')
    const res: any = await request.get('/users/me')
    user.value = res.data
  } catch { clearAuth() }
}

/** 退出登录：只清前端状态。服务端无状态，不会作废已签发的 token */
function clearAuth() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  return { token, user, isLoggedIn, setAuth, clearAuth, fetchUser }
})
