/** ChenManus 2.0 API */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api'

export const ENDPOINTS = {
  loveChat: `${API_BASE}/ai/love/chat/sse`,
  manusChat: `${API_BASE}/ai/manus/chat`,
  tasks: `${API_BASE}/tasks`,
  task: (taskId) => `${API_BASE}/tasks/${taskId}`,
  taskEvents: (taskId) => `${API_BASE}/tasks/${taskId}/events`,
  taskPause: (taskId) => `${API_BASE}/tasks/${taskId}/pause`,
  taskResume: (taskId) => `${API_BASE}/tasks/${taskId}/resume`,
  taskCancel: (taskId) => `${API_BASE}/tasks/${taskId}/cancel`,
}
