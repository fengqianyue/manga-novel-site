import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截器：自动携带 JWT Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('admin_token') || localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code !== 200) {
      if (!response.config.silent) {
        ElMessage.error(data.message || '请求失败')
      }
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (error) => {
    if (!error.config?.silent) {
      if (error.response?.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('admin_token')
        ElMessage.error('登录已过期，请重新登录')
      } else {
        ElMessage.error(error.message || '网络异常')
      }
    }
    return Promise.reject(error)
  },
)

export default request
