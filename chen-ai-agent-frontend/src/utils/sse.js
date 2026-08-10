/**
 * 通过 fetch + ReadableStream 消费 SSE 流式响应
 * @param {string} url - 请求地址
 * @param {object} options
 * @param {function(string): void} options.onToken - 每收到一段文本时回调
 * @param {function(Error): void} [options.onError] - 错误回调
 * @param {function(): void} [options.onDone] - 流结束回调
 * @param {AbortSignal} [options.signal] - 取消信号
 */
export async function streamSSE(url, { onToken, onError, onDone, signal }) {
  const response = await fetch(url, {
    headers: { Accept: 'text/event-stream' },
    signal,
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    buffer = processBuffer(buffer, onToken)
  }

  // 处理剩余 buffer
  if (buffer.trim()) {
    extractTokens(buffer, onToken)
  }

  onDone?.()
}

/** 按行处理 buffer，返回未完成的剩余部分 */
function processBuffer(buffer, onToken) {
  const lines = buffer.split('\n')
  const remaining = lines.pop() ?? ''

  for (const line of lines) {
    extractTokens(line, onToken)
  }

  return remaining
}

/** 从单行中提取 SSE data 或纯文本 token */
function extractTokens(line, onToken) {
  const trimmed = line.trimEnd()
  if (!trimmed || trimmed.startsWith(':')) return

  if (trimmed.startsWith('data:')) {
    let data = trimmed.slice(5)
    // 保留 leading space（SSE 规范允许 data: 后有一个空格）
    if (data.startsWith(' ')) data = data.slice(1)

    if (!data || data === '[DONE]') return

    // 尝试解析 data：如果是 JSON 且有 content 字段，只取 content
    const token = extractContentFromData(data)
    if (token) {
      onToken(token)
    }
  } else if (!trimmed.startsWith('event:') && !trimmed.startsWith('id:') && !trimmed.startsWith('retry:')) {
    onToken(trimmed)
  }
}

/**
 * 从 SSE data 字符串中提取文本内容
 * - 如果是 JSON 且包含 content 字段，返回 content
 * - 如果是结束信号（error 字段），返回 null（忽略）
 * - 否则返回原数据
 */
function extractContentFromData(data) {
  const firstChar = data.charAt(0)
  if (firstChar !== '{' && firstChar !== '[') return data

  try {
    const obj = JSON.parse(data)
    if (obj && typeof obj === 'object') {
      // 结束信号：{"error": false/true, "message": "..."}，不输出
      if ('error' in obj && typeof obj.content !== 'string') {
        return null
      }
      // 正常 token
      if (typeof obj.content === 'string') {
        return obj.content
      }
    }
  } catch {
    // 不是 JSON，按纯文本处理
  }
  return data
}
