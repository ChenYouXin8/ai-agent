<script setup>
import { ref } from 'vue'

defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
  placeholder: {
    type: String,
    default: '输入你的问题，按回车发送',
  },
})

const emit = defineEmits(['submit'])

const input = ref('')

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submit()
  }
}

function submit() {
  const text = input.value.trim()
  if (!text) return
  emit('submit', text)
  input.value = ''
}
</script>

<template>
  <div class="chat-input">
    <textarea
      v-model="input"
      class="chat-input__field"
      :placeholder="placeholder"
      :disabled="disabled"
      rows="1"
      @keydown="handleKeydown"
    />
    <button class="chat-input__btn" :disabled="disabled || !input.trim()" @click="submit">
      发送
    </button>
  </div>
</template>

<style scoped>
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 16px;
  background: var(--color-surface);
  border-top: 1px solid #e5e7eb;
}

.chat-input__field {
  flex: 1;
  resize: none;
  padding: 10px 14px;
  border: 1px solid #d1d5db;
  border-radius: var(--radius-md);
  font-size: 15px;
  line-height: 1.5;
  max-height: 120px;
  transition: border-color 0.2s;
}

.chat-input__field:focus {
  border-color: var(--color-primary);
}

.chat-input__field:disabled {
  background: #f9fafb;
  cursor: not-allowed;
}

.chat-input__btn {
  flex-shrink: 0;
  padding: 10px 20px;
  background: var(--color-primary);
  color: #fff;
  border-radius: var(--radius-md);
  font-size: 15px;
  font-weight: 500;
  transition: background 0.2s;
}

.chat-input__btn:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.chat-input__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
