import request from './request'

// ====== 管理员登录 ======
export function adminLogin(data) {
  return request.post('/user/admin-login', data)
}

// ====== 作品管理 ======
export function addWork(data) {
  return request.post('/work', data)
}
export function updateWork(data) {
  return request.put('/work', data)
}
export function toggleWorkStatus(id, status) {
  return request.put('/work/status', null, { params: { id, status } })
}
export function updateWorkTags(workId, tagIds) {
  return request.put(`/work/${workId}/tags`, { tagIds })
}

// ====== 标签管理 ======
export function getTags() {
  return request.get('/tag/list')
}
export function addTag(name) {
  return request.post('/tag', { name })
}
export function deleteTag(id) {
  return request.delete(`/tag/${id}`)
}

// ====== 章节管理 ======
export function getChapters(workId) {
  return request.get(`/chapter/list/${workId}`)
}
export function addChapter(data) {
  return request.post('/chapter', data)
}
export function deleteChapter(id) {
  return request.delete(`/chapter/${id}`)
}
