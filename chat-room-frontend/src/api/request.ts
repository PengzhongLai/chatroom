import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

// 全局 Axios 实例。baseURL 带 /api 前缀，所以调用时写 '/auth/login' 即可
const request = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000
})

// 请求拦截器：自动把 JWT 加到 Authorization 头。
// 登录请求时 store 里还没有 token，因此这个头不会加上——这也是后端必须放行 /api/auth/** 的原因
request.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

// 响应拦截器：直接返回 response.data（后端 ApiResponse 信封），
// 所以调用方拿到的是 { code, message, data }，业务数据在 .data 里
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || '请求失败'
    console.error(message)
    return Promise.reject(error)
  }
)

export default request
