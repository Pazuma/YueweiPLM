import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL || ''}/api/v1`,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('plm_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Request-Id'] = crypto.randomUUID()
  return config
})

request.interceptors.response.use(
  (response) => response,
  (error) => {
    ElMessage.error(error.response?.data?.message || '网络请求失败')
    return Promise.reject(error)
  }
)

export function mockResolve<T>(factory: () => T, delay = 180): Promise<T> {
  return new Promise((resolve, reject) => {
    window.setTimeout(() => {
      try {
        resolve(factory())
      } catch (error) {
        reject(error)
      }
    }, delay)
  })
}

export default request
