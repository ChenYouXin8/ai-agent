import { ref, nextTick } from 'vue'
import { streamSSE } from '../utils/sse'

/**
 * 通用 SSE 聊天 composable
 * @param {function(string): string} buildUrl - 根据用户消息构建请求 URL
 */
export function useSSEChat(buildUrl) {
  const messages = ref([])
  const loading = ref(false)
  let abortController = null

  async function send(userMessage) {
    if (loading.value) return

    messages.value.push({ role: 'user', content: userMessage })

    const aiIndex = messages.value.length
    messages.value.push({ role: 'ai', content: '', streaming: true })

    loading.value = true
    abortController = new AbortController()

    try {
      const url = buildUrl(userMessage)
      await streamSSE(url, {
        signal: abortController.signal,
        onToken: (token) => {
          messages.value[aiIndex].content += token
        },
        onDone: () => {
          messages.value[aiIndex].streaming = false
        },
      })
    } catch (err) {
      if (err.name === 'AbortError') return

      // 连接失败
      if (messages.value[aiIndex].content === '') {
        messages.value.splice(aiIndex, 1)
        messages.value.push({
          role: 'system',
          content: '连接失败，请检查后端服务是否启动',
        })
      } else {
        // 流式中断
        messages.value[aiIndex].streaming = false
        messages.value.push({
          role: 'system',
          content: 'AI 回复异常中断，请重试',
        })
      }
    } finally {
      loading.value = false
      if (messages.value[aiIndex]) {
        messages.value[aiIndex].streaming = false
      }
    }
  }

  return { messages, loading, send }
}

/** 滚动聊天区域到底部 */
export function useAutoScroll(chatRef) {
  async function scrollToBottom() {
    await nextTick()
    if (chatRef.value) {
      chatRef.value.scrollTop = chatRef.value.scrollHeight
    }
  }
  return { scrollToBottom }
}
