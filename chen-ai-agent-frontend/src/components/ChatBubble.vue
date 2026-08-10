<script setup>
defineProps({
  role: {
    type: String,
    required: true,
    validator: (v) => ['user', 'ai', 'system'].includes(v),
  },
  content: {
    type: String,
    default: '',
  },
  streaming: {
    type: Boolean,
    default: false,
  },
})

const avatars = { user: '👤', ai: '🤖', system: '⚠️' }
</script>

<template>
  <div class="bubble-row" :class="[`bubble-row--${role}`]">
    <div v-if="role !== 'user'" class="avatar">{{ avatars[role] }}</div>

    <div class="bubble" :class="[`bubble--${role}`]">
      <template v-if="role === 'ai' && streaming && !content">
        <span class="thinking">
          思考中<span class="dots"><span>.</span><span>.</span><span>.</span></span>
        </span>
      </template>
      <template v-else>
        <span class="content">{{ content }}</span>
        <span v-if="role === 'ai' && streaming && content" class="cursor">|</span>
      </template>
    </div>

    <div v-if="role === 'user'" class="avatar">{{ avatars.user }}</div>
  </div>
</template>

<style scoped>
.bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
}

.bubble-row--user {
  flex-direction: row-reverse;
}

.bubble-row--system {
  justify-content: center;
}

.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  border-radius: 50%;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  word-break: break-word;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 15px;
}

.bubble--user {
  background: var(--color-user-bubble);
  border: 1px solid var(--color-user-border);
  border-bottom-right-radius: 4px;
}

.bubble--ai {
  background: var(--color-ai-bubble);
  border: 1px solid var(--color-ai-border);
  border-bottom-left-radius: 4px;
}

.bubble--system {
  max-width: 90%;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: var(--color-error);
  font-size: 14px;
  text-align: center;
  border-radius: var(--radius-sm);
}

.content {
  white-space: pre-wrap;
}

.cursor {
  display: inline;
  animation: blink 1s step-end infinite;
  color: var(--color-primary);
  font-weight: 300;
}

.thinking {
  color: var(--color-text-muted);
  font-style: italic;
}

.dots span {
  animation: dot-bounce 1.4s infinite;
  opacity: 0;
}

.dots span:nth-child(1) { animation-delay: 0s; }
.dots span:nth-child(2) { animation-delay: 0.2s; }
.dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  50% { opacity: 0; }
}

@keyframes dot-bounce {
  0%, 80%, 100% { opacity: 0; }
  40% { opacity: 1; }
}
</style>
