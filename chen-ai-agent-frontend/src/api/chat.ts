// 对接 Chen AI Agent 自有 Spring Boot 后端的流式对话客户端
// 后端 SSE 约定（智能助手 / 恋爱大师一致）：
//   event:message  data:{"step":1,"content":"..."}
//   event:done     data:{"error":false,"message":""}
//   event:error    data:{"error":true,"message":"..."}

const BASE = import.meta.env.VITE_GLOB_API_URL || ''

export type AssistantMode = 'assistant' | 'love'

export interface AssistantDef {
  mode: AssistantMode
  /** 侧边栏展示名称 */
  title: string
  /** 副标题/描述 */
  description: string
  /** 图标（iconify） */
  icon: string
  /** 占位提示语 */
  placeholder: string
  /** 构造请求地址 */
  buildUrl: (message: string, chatId: string) => string
}

export const ASSISTANTS: Record<AssistantMode, AssistantDef> = {
  // 智能助手：ChenManus 工具调用智能体（SSE）
  assistant: {
    mode: 'assistant',
    title: '智能助手',
    description: 'ChenManus 工具调用智能体',
    icon: 'ri:robot-2-line',
    placeholder: '和智能助手聊聊，它会调用工具帮你完成任务……',
    buildUrl: message =>
      `${BASE}/ai/manus/chat?message=${encodeURIComponent(message)}`,
  },
  // AI 恋爱大师：恋爱专家（RAG + MCP，SSE 打字机）
  love: {
    mode: 'love',
    title: 'AI 恋爱大师',
    description: '恋爱专家 · RAG 知识库 + MCP 工具',
    icon: 'ri:heart-3-line',
    placeholder: '说说你的情感困惑，恋爱大师为你分析……',
    buildUrl: (message, chatId) =>
      `${BASE}/ai/love/chat/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`,
  },
}

export const ASSISTANT_LIST: AssistantDef[] = [ASSISTANTS.assistant, ASSISTANTS.love]

export interface StreamHandlers {
  /** 收到一段文本增量 */
  onToken: (token: string) => void
  /** 后端返回错误 */
  onError?: (message: string) => void
  /** 流正常结束 */
  onDone?: () => void
  /** 取消信号 */
  signal?: AbortSignal
}

function handleData(event: string, data: string, handlers: StreamHandlers) {
  if (!data || data === '[DONE]')
    return

  let payload: any = null
  try {
    payload = JSON.parse(data)
  }
  catch {
    payload = null
  }

  if (event === 'message') {
    if (payload && typeof payload.content === 'string') {
      handlers.onToken(payload.content)
    }
    else if (payload && typeof payload.message === 'string' && payload.error === false && payload.message) {
      handlers.onToken(payload.message)
    }
    else if (!payload) {
      // 非 JSON，按纯文本处理
      handlers.onToken(data)
    }
    return
  }

  if (event === 'error') {
    const msg = (payload && payload.message) ? String(payload.message) : '请求失败'
    handlers.onError?.(msg)
    return
  }

  if (event === 'done') {
    if (payload && payload.error)
      handlers.onError?.(payload.message ? String(payload.message) : '请求失败')
    else
      handlers.onDone?.()
  }
}

/**
 * 发起一次流式对话。
 * @param mode    助手模式
 * @param message 用户消息
 * @param chatId  会话 ID（恋爱大师用其维持上下文）
 */
export async function streamChat(
  mode: AssistantMode,
  message: string,
  chatId: string,
  handlers: StreamHandlers,
) {
  const assistant = ASSISTANTS[mode] ?? ASSISTANTS.assistant
  const url = assistant.buildUrl(message, chatId)

  const response = await fetch(url, {
    method: 'GET',
    headers: { Accept: 'text/event-stream' },
    signal: handlers.signal,
  })

  if (!response.ok || !response.body)
    throw new Error(`HTTP ${response.status}`)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'message'

  while (true) {
    const { done, value } = await reader.read()
    if (done)
      break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const rawLine of lines) {
      const line = rawLine.replace(/\r$/, '')
      if (!line.trim()) {
        // 空行是事件分隔符，重置默认事件
        currentEvent = 'message'
        continue
      }
      if (line.startsWith(':')) {
        // SSE 注释
        continue
      }
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      }
      else if (line.startsWith('data:')) {
        let data = line.slice(5)
        if (data.startsWith(' '))
          data = data.slice(1)
        handleData(currentEvent, data, handlers)
      }
    }
  }

  // 处理残留 buffer
  if (buffer.trim()) {
    const line = buffer.replace(/\r$/, '')
    if (line.startsWith('data:')) {
      let data = line.slice(5)
      if (data.startsWith(' '))
        data = data.slice(1)
      handleData(currentEvent, data, handlers)
    }
  }

  handlers.onDone?.()
}
