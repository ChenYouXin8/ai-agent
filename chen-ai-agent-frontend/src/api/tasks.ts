/**
 * ChenManus 2.0 任务工作区 API
 * 对接 Spring Boot 后端 /tasks 系列接口（统一响应体 { code, message, data }，code === 0 为成功）。
 */

const BASE = import.meta.env.VITE_GLOB_API_URL || ''

export interface TaskScope {
  tenantId: string
  userId: string
  sessionId: string
}

export interface QuotaView {
  tenantId: string
  activeTasks: number
  maxActiveTasksPerTenant: number
}

export interface TaskStep {
  stepId: string
  title: string
  description?: string
  status?: string
  dependsOn?: string[]
  parallelizable?: boolean
  approvalStatus?: string
  approvalNote?: string
  retryCount?: number
  durationMs?: number
  actualInputTokens?: number
  estimatedInputTokens?: number
  actualOutputTokens?: number
  estimatedOutputTokens?: number
  modelCallCount?: number
  output?: string
  error?: string
}

export interface TaskArtifact {
  artifactId: string
  type: string
  version?: number
  name: string
  path?: string
  checksum?: string
  mediaType?: string
}

export interface TaskEvent {
  eventId?: string
  timestamp?: string
  type?: string
  message?: string
  [key: string]: any
}

export interface ChenTask {
  taskId: string
  title?: string
  prompt?: string
  status: string
  priority?: string
  planSummary?: string
  steps?: TaskStep[]
  artifacts?: TaskArtifact[]
  review?: { passed: boolean; feedback?: string; missingItems?: string }
  metrics?: Record<string, any>
  result?: string
  durationMs?: number
  actualInputTokens?: number
  estimatedInputTokens?: number
  actualOutputTokens?: number
  estimatedOutputTokens?: number
  modelCallCount?: number
}

export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL'

interface ApiResponse<T> {
  code: number
  message?: string
  data: T
}

function scopeQuery(scope: TaskScope, withSession = false) {
  const params = new URLSearchParams({
    tenantId: scope.tenantId,
    userId: scope.userId,
  })
  if (withSession)
    params.append('sessionId', scope.sessionId)
  return `?${params.toString()}`
}

async function requestJson<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, options)
  if (!response.ok)
    throw new Error(`HTTP ${response.status}`)
  const body: ApiResponse<T> = await response.json()
  if (body.code !== 0)
    throw new Error(body.message || '请求失败')
  return body.data
}

export const TaskApi = {
  create(scope: TaskScope, prompt: string, priority: TaskPriority) {
    return requestJson<ChenTask>(`${BASE}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt, tenantId: scope.tenantId, userId: scope.userId, sessionId: scope.sessionId, priority }),
    })
  },

  list(scope: TaskScope) {
    return requestJson<ChenTask[]>(`${BASE}/tasks${scopeQuery(scope, true)}`)
  },

  get(scope: TaskScope, taskId: string) {
    return requestJson<ChenTask>(`${BASE}/tasks/${taskId}${scopeQuery(scope)}`)
  },

  quota(scope: TaskScope) {
    return requestJson<QuotaView>(`${BASE}/tasks/quota?${new URLSearchParams({ tenantId: scope.tenantId })}`)
  },

  action(scope: TaskScope, taskId: string, action: 'pause' | 'resume' | 'cancel', note?: string) {
    return requestJson<ChenTask>(`${BASE}/tasks/${taskId}/${action}${scopeQuery(scope)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note: note ?? '' }),
    })
  },

  approve(scope: TaskScope, taskId: string, note: string) {
    return requestJson<ChenTask>(`${BASE}/tasks/${taskId}/approve${scopeQuery(scope)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note }),
    })
  },

  reject(scope: TaskScope, taskId: string, note: string) {
    return requestJson<ChenTask>(`${BASE}/tasks/${taskId}/reject${scopeQuery(scope)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note }),
    })
  },

  eventsUrl(scope: TaskScope, taskId: string) {
    return `${BASE}/tasks/${taskId}/events${scopeQuery(scope)}`
  },

  eventHistory(scope: TaskScope, taskId: string) {
    return requestJson<TaskEvent[]>(`${BASE}/tasks/${taskId}/events/history${scopeQuery(scope)}`)
  },

  artifactUrl(scope: TaskScope, taskId: string, artifactId: string, preview: boolean) {
    const suffix = preview ? 'preview' : 'download'
    return `${BASE}/tasks/${taskId}/artifacts/${artifactId}/${suffix}${scopeQuery(scope)}`
  },
}

/** 读取或初始化本地 scope（租户 / 用户 / 会话） */
export function loadScope(): TaskScope {
  const tenantId = localStorage.getItem('chenmanus-tenant-id') || 'default'
  const userId = localStorage.getItem('chenmanus-user-id') || crypto.randomUUID()
  const sessionId = sessionStorage.getItem('chenmanus-session-id') || crypto.randomUUID()
  localStorage.setItem('chenmanus-tenant-id', tenantId)
  localStorage.setItem('chenmanus-user-id', userId)
  sessionStorage.setItem('chenmanus-session-id', sessionId)
  return { tenantId, userId, sessionId }
}
