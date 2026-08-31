import request from './request'

export function searchMessages(keyword: string, page = 0, size = 20) {
  return request.get('/search/messages', { params: { q: keyword, page, size } })
}
