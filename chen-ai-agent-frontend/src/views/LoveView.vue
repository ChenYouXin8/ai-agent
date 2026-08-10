<script setup>
import { ref, watch, onMounted } from 'vue'
import ChatBubble from '../components/ChatBubble.vue'
import ChatInput from '../components/ChatInput.vue'
import { useSSEChat, useAutoScroll } from '../composables/useSSEChat.js'
import { getOrCreateChatId } from '../utils/uuid.js'
import { ENDPOINTS } from '../config/api.js'

const CHAT_ID_KEY = 'love_chat_id'
const chatId = ref('')
const chatRef = ref(null)

const { messages, loading, send } = useSSEChat((msg) => {
  const params = new URLSearchParams({
    message: msg,
    chatId: chatId.value,
  })
  return `${ENDPOINTS.loveChat}?${params}`
})

const { scrollToBottom } = useAutoScroll(chatRef)

onMounted(() => {
  chatId.value = getOrCreateChatId(CHAT_ID_KEY)
})

watch(messages, scrollToBottom, { deep: true })

async function handleSubmit(text) {
  await send(text)
  scrollToBottom()
}

async function copyChatId() {
  try {
    await navigator.clipboard.writeText(chatId.value)
  } catch {
    // fallback
    const el = document.createElement('textarea')
    el.value = chatId.value
    document.body.appendChild(el)
    el.select()
    document.execCommand('copy')
    document.body.removeChild(el)
  }
}
</script>

<template>
  <div class="chat-page">
    <header class="chat-page__header">
      <div class="chat-page__header-left">
        <router-link to="/" class="back-btn" title="返回首页">←</router-link>
        <h1 class="chat-page__title">AI 恋爱大师</h1>
      </div>
      <button class="chat-id" title="点击复制 chatId" @click="copyChatId">
        chatId: {{ chatId.slice(0, 8) }}...
      </button>
    </header>

    <div ref="chatRef" class="chat-page__messages">
      <div v-if="messages.length === 0" class="chat-page__empty">
        <span class="empty-icon">💕</span>
        <p>你好，我是 AI 恋爱大师，有什么情感问题想聊聊吗？</p>
      </div>
      <ChatBubble
        v-for="(msg, i) in messages"
        :key="i"
        :role="msg.role"
        :content="msg.content"
        :streaming="msg.streaming"
      />
    </div>

    <ChatInput :disabled="loading" @submit="handleSubmit" />
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-bg);
}

.chat-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--color-surface);
  border-bottom: 1px solid #e5e7eb;
  box-shadow: var(--shadow-sm);
  flex-shrink: 0;
}

.chat-page__header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  font-size: 18px;
  color: var(--color-text-muted);
  transition: background 0.2s;
}

.back-btn:hover {
  background: #f3f4f6;
}

.chat-page__title {
  font-size: 1.125rem;
  font-weight: 600;
}

.chat-id {
  font-size: 12px;
  color: var(--color-text-muted);
  background: #f3f4f6;
  padding: 4px 10px;
  border-radius: 20px;
  cursor: pointer;
  transition: background 0.2s;
  font-family: monospace;
}

.chat-id:hover {
  background: #e5e7eb;
}

.chat-page__messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.chat-page__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-muted);
  text-align: center;
  gap: 12px;
}

.empty-icon {
  font-size: 48px;
}
</style>
