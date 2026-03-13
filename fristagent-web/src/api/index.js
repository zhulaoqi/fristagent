import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000,
})

api.interceptors.response.use(
  res => res.data,
  err => {
    const msg = err.response?.data?.error || err.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

// ---- Scans ----
export const scanApi = {
  list: (params) => api.get('/scans', { params }),
  get: (id) => api.get(`/scans/${id}`),
  getIssues: (id) => api.get(`/scans/${id}/issues`),
  stats: () => api.get('/scans/stats'),
}

// ---- Skills ----
export const skillApi = {
  list: () => api.get('/skills'),
  getActive: () => api.get('/skills/active'),
  activate: (name) => api.put(`/skills/${name}/activate`),
  install: (data) => api.post('/skills/install', data),
  uninstall: (name) => api.delete(`/skills/${name}`),
}

// ---- Repos ----
export const repoApi = {
  list: () => api.get('/repos'),
  create: (data) => api.post('/repos', data),
  update: (id, data) => api.put(`/repos/${id}`, data),
  remove: (id) => api.delete(`/repos/${id}`),
}

// ---- Admins ----
export const adminApi = {
  list: () => api.get('/admins'),
  create: (data) => api.post('/admins', data),
  update: (id, data) => api.put(`/admins/${id}`, data),
  remove: (id) => api.delete(`/admins/${id}`),
}

// ---- LLM Config ----
export const llmApi = {
  get: () => api.get('/config/llm'),
  save: (data) => api.post('/config/llm', data),
}

// ---- Notify Config ----
export const notifyApi = {
  get: () => api.get('/config/notify'),
  save: (data) => api.post('/config/notify', data),
  testFeishu: (openId) => api.post('/config/notify/test/feishu', { openId }),
  testEmail: (email) => api.post('/config/notify/test/email', { email }),
}

// ---- Chat ----
export const chatApi = {
  send: (data) => api.post('/chat/send', data),
  history: (sessionId) => api.get(`/chat/history/${sessionId}`),
}

export default api
