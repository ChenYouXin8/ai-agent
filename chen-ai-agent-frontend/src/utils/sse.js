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
    const data = trimmed.slice(5)
    // 保留 leading space（SSE 规范允许 data: 后有一个空格）
    const token = data.startsWith(' ') ? data.slice(1) : data
    if (token && token !== '[DONE]') {
      onToken(token)
    }
  } else if (!trimmed.startsWith('event:') && !trimmed.startsWith('id:') && !trimmed.startsWith('retry:')) {
    onToken(trimmed)
  }
}
