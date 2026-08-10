<script setup>
import { ref, watch } from 'vue'
import ChatBubble from '../components/ChatBubble.vue'
import ChatInput from '../components/ChatInput.vue'
import { useSSEChat, useAutoScroll } from '../composables/useSSEChat.js'
import { ENDPOINTS } from '../config/api.js'

const chatRef = ref(null)

const { messages, loading, send } = useSSEChat((msg) => {
  const params = new URLSearchParams({ message: msg })
  return `${ENDPOINTS.manusChat}?${params}`
})

const { scrollToBottom } = useAutoScroll(chatRef)

watch(messages, scrollToBottom, { deep: true })

async function handleSubmit(text) {
  await send(text)
  scrollToBottom()
}
</script>

<template>
  <div class="chat-page">
    <header class="chat-page__header">
      <div class="chat-page__header-left">
        <router-link to="/" class="back-btn" title="返回首页">←</router-link>
        <h1 class="chat-page__title">AI 超级智能体</h1>
      </div>
    </header>

    <div ref="chatRef" class="chat-page__messages">
      <div v-if="messages.length === 0" class="chat-page__empty">
        <span class="empty-icon">🚀</span>
        <p>你好，我是 AI 超级智能体，告诉我你想完成什么任务吧！</p>
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
