import request from './request'

export interface ChannelInfo {
  id: number
  name: string
  description: string
  creator: { id: number; username: string; nickname: string }
  isPublic: boolean
  inviteCode?: string
  isMuted: boolean
  createdAt: string
}

export interface ChannelMember {
  id: number
  channel: { id: number; name?: string }
  user: { id: number; username: string; nickname: string; avatarUrl?: string; status: string }
  role: 'CREATOR' | 'ADMIN' | 'MEMBER'
  historyLevel: 'NONE' | 'LIMITED' | 'ALL'
  historyLimit?: number
  joinedAt: string
}

export function listChannels(keyword?: string, page = 0, size = 20) {
  return request.get('/channels', { params: { keyword, page, size } })
}

export function getChannel(id: number) {
  return request.get(`/channels/${id}`)
}

export function createChannel(name: string, description: string, isPublic: boolean) {
  return request.post('/channels', { name, description, isPublic })
}

export function updateChannel(id: number, name: string, description: string) {
  return request.put(`/channels/${id}`, { name, description })
}

export function deleteChannel(id: number) {
  return request.delete(`/channels/${id}`)
}

export function joinChannel(id: number, inviteCode?: string) {
  return request.post(`/channels/${id}/join`, inviteCode ? { inviteCode } : undefined)
}

export function leaveChannel(id: number) {
  return request.post(`/channels/${id}/leave`)
}

export function inviteMember(id: number, userId: number, historyLevel: string, historyLimit?: number) {
  return request.post(`/channels/${id}/invite`, { userId, historyLevel, historyLimit })
}

export function toggleMute(id: number) {
  return request.put(`/channels/${id}/mute`)
}

export function getMembers(id: number) {
  return request.get(`/channels/${id}/members`)
}

export function updateMember(id: number, userId: number, action: string, role?: string) {
  return request.put(`/channels/${id}/members/${userId}`, { action, role })
}

export function transferChannel(id: number, userId: number) {
  return request.put(`/channels/${id}/transfer`, { userId })
}

export function promoteToAdmin(id: number, userId: number) {
  return request.put(`/channels/${id}/promote`, { userId })
}

export function demoteToMember(id: number, userId: number) {
  return request.put(`/channels/${id}/demote`, { userId })
}

export function joinByCode(inviteCode: string) {
  return request.post('/channels/join-by-code', { inviteCode })
}

export function myChannels() {
  return request.get('/channels/my')
}
