import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Response interceptor: unwrap data, surface errors
api.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.success === false && body.error) {
      ElMessage.error(body.error.message || 'Request failed')
      return Promise.reject(body.error)
    }
    return body
  },
  (error) => {
    const msg = error.response?.data?.error?.message || error.message || 'Network error'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default api
