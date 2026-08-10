/** 后端 API 基础路径，开发环境通过 Vite 代理到 localhost:8123 */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api'

export const ENDPOINTS = {
  loveChat: `${API_BASE}/ai/love_app/chat/sse`,
  manusChat: `${API_BASE}/ai/manus/chat`,
}
