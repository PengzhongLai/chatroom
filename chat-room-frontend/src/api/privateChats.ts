import request from './request'

export function initiateChat(targetUserId: number) {
  return request.post('/private-chats', { targetUserId })
}

export function listChats() {
  return request.get('/private-chats')
}

export function getMessages(chatId: number, page = 0, size = 50) {
  return request.get(`/private-chats/${chatId}/messages`, { params: { page, size } })
}

export function acceptChat(chatId: number) {
  return request.post(`/private-chats/${chatId}/accept`)
}

export function rejectChat(chatId: number) {
  return request.post(`/private-chats/${chatId}/reject`)
}

export function deleteChat(chatId: number) {
  return request.delete(`/private-chats/${chatId}`)
}
