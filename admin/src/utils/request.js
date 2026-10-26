import axios from 'axios'
import { ElMessage } from 'element-plus'
import { session, clearSession } from './auth'

const request = axios.create({ baseURL: '/api/admin', timeout: 20000 })

request.interceptors.request.use((config) => {
  if (session.token) config.headers.Authorization = `Bearer ${session.token}`
  return config
})

function toLogin() {
  clearSession()
  const base = import.meta.env.BASE_URL
  if (!location.pathname.startsWith(`${base}login`)) {
    const redirect = encodeURIComponent(location.pathname.slice(base.length - 1) + location.search)
    location.href = `${base}login?redirect=${redirect}`
  }
}

request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (!body || typeof body !== 'object' || !('code' in body)) return body
    if (body.code === 0) return body.data
    if (body.code === 401) {
      ElMessage.error(body.msg || '登录已过期，请重新登录')
      toLogin()
    } else {
      ElMessage.error(body.msg || '操作失败')
    }
    return Promise.reject(body)
  },
  (err) => {
    if (err.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      toLogin()
    } else {
      ElMessage.error(err.response?.data?.msg || (err.code === 'ECONNABORTED' ? '请求超时' : '网络错误，请稍后再试'))
    }
    return Promise.reject(err)
  }
)

export default request
