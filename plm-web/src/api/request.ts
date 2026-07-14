import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL || ''}/api/v1`,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export function prepareRequestContentType(config: InternalAxiosRequestConfig) {
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
}

request.interceptors.request.use((config) => {
  prepareRequestContentType(config)
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

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId?: string
  timestamp?: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function unwrapResponse<T>(response: AxiosResponse<ApiResponse<T>>): T {
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '接口请求失败')
  }
  return body.data
}

export function unwrapPage<T>(response: AxiosResponse<ApiResponse<PageResponse<T>>>): PageResponse<T> {
  return unwrapResponse(response)
}

export default request
