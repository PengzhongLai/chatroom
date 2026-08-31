import request from './request'
import type { LoginResponse } from '@/types'

export function login(username: string, password: string) {
  return request.post<any, { code: number; message: string; data: LoginResponse }>('/auth/login', { username, password })
}

export function register(username: string, password: string, nickname?: string) {
  return request.post('/auth/register', { username, password, nickname })
}
