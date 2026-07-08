import request from './request'

// 公开接口
export function getWorkList(params) {
  return request.get('/work/list', { params })
}
export function getWorkDetail(id) {
  return request.get(`/work/${id}`)
}
export function getWorkTags(workId) {
  return request.get(`/work/${workId}/tags`)
}
