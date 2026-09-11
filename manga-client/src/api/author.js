import request from './request'

// ====== 作者中心：我的作品 ======
export function getMyWorks(params) {
  return request.get('/author/works', { params })
}
export function createMyWork(data) {
  return request.post('/author/works', data)
}
export function updateMyWork(data) {
  return request.put('/author/works', data)
}
export function submitMyWork(id) {
  return request.put(`/author/works/${id}/submit`, null, { silent: true })
}
export function withdrawMyWork(id) {
  return request.put(`/author/works/${id}/withdraw`, null, { silent: true })
}
export function offlineMyWork(id) {
  return request.put(`/author/works/${id}/offline`, null, { silent: true })
}
