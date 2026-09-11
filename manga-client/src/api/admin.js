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
export function auditWork(id, pass, reason) {
  return request.put('/work/audit', null, { params: { id, pass, reason } })
}
export function updateWorkTags(workId, tagIds) {
  return request.put(`/work/${workId}/tags`, { tagIds })
}

// ====== 用户管理 ======
export function setUserRole(id, value) {
  return request.put(`/user/${id}/role`, null, { params: { value } })
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

// ====== 作品整部导入 ======
// 漫画：ZIP/CBZ（子文件夹=章节，内含按序命名的页图）
export function importManga(formData) {
  return request.post('/import/manga', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
  })
}
// 小说：TXT（自动分章+编码识别）/ EPUB（按 spine 分章）
export function importNovel(formData) {
  return request.post('/import/novel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
  })
}
