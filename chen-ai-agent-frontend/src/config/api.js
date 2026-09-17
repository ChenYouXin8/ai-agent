/** ChenManus 2.6 API */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api'

export const ENDPOINTS = {
  loveChat: `${API_BASE}/ai/love/chat/sse`,
  manusChat: `${API_BASE}/ai/manus/chat`,
  tasks: `${API_BASE}/tasks`,
  task: (taskId, query = '') => `${API_BASE}/tasks/${taskId}${query}`,
  taskEvents: (taskId, query = '') => `${API_BASE}/tasks/${taskId}/events${query}`,
  taskPause: (taskId, query = '') => `${API_BASE}/tasks/${taskId}/pause${query}`,
  taskResume: (taskId, query = '') => `${API_BASE}/tasks/${taskId}/resume${query}`,
  taskCancel: (taskId, query = '') => `${API_BASE}/tasks/${taskId}/cancel${query}`,
  artifact: (taskId, artifactId, query = '') => `${API_BASE}/tasks/${taskId}/artifacts/${artifactId}/download${query}`,
}
