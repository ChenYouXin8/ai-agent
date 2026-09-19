<script setup lang='ts'>
import type { CSSProperties } from 'vue'
import { computed, ref, watch } from 'vue'
import { NButton, NLayoutSider, useDialog } from 'naive-ui'
import { useRouter } from 'vue-router'
import List from './List.vue'
import Footer from './Footer.vue'
import { useAppStore, useChatStore } from '@/store'
import { useBasicLayout } from '@/hooks/useBasicLayout'
import { PromptStore, SvgIcon } from '@/components/common'
import { ASSISTANT_LIST } from '@/api/chat'
import { t } from '@/locales'

const appStore = useAppStore()
const chatStore = useChatStore()
const router = useRouter()

const dialog = useDialog()

const { isMobile } = useBasicLayout()
const show = ref(false)

const collapsed = computed(() => appStore.siderCollapsed)
const activeMode = computed<Chat.AssistantMode>(() => chatStore.getModeByUuid(chatStore.active))

function selectMode(mode: Chat.AssistantMode) {
  chatStore.openApp(mode)
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function handleAdd() {
  chatStore.setNewChatMode(activeMode.value)
  chatStore.addHistory({ title: t('chat.newChatTitle'), uuid: Date.now(), isEdit: false })
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function goWorkspace() {
  router.push({ name: 'Workspace' })
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function handleUpdateCollapsed() {
  appStore.setSiderCollapsed(!collapsed.value)
}

function handleClearAll() {
  dialog.warning({
    title: t('chat.deleteMessage'),
    content: t('chat.clearHistoryConfirm'),
    positiveText: t('common.yes'),
    negativeText: t('common.no'),
    onPositiveClick: () => {
      chatStore.clearHistory()
      if (isMobile.value)
        appStore.setSiderCollapsed(true)
    },
  })
}

const getMobileClass = computed<CSSProperties>(() => {
  if (isMobile.value) {
    return {
      position: 'fixed',
      zIndex: 50,
    }
  }
  return {}
})

const mobileSafeArea = computed(() => {
  if (isMobile.value) {
    return {
      paddingBottom: 'env(safe-area-inset-bottom)',
    }
  }
  return {}
})

watch(
  isMobile,
  (val) => {
    appStore.setSiderCollapsed(val)
  },
  {
    immediate: true,
    flush: 'post',
  },
)
</script>

<template>
  <NLayoutSider
    :collapsed="collapsed"
    :collapsed-width="0"
    :width="260"
    :show-trigger="isMobile ? false : 'arrow-circle'"
    collapse-mode="transform"
    position="absolute"
    bordered
    :style="getMobileClass"
    @update-collapsed="handleUpdateCollapsed"
  >
    <div class="flex flex-col h-full" :style="mobileSafeArea">
      <main class="flex flex-col flex-1 min-h-0">
        <div class="flex flex-col gap-2 p-4">
          <NButton block secondary @click="goWorkspace">
            <template #icon>
              <SvgIcon icon="ri:play-list-line" />
            </template>
            ChenManus 工作区
          </NButton>
          <div class="flex gap-2">
            <button
              v-for="m in ASSISTANT_LIST"
              :key="m.mode"
              class="flex flex-1 items-center justify-center gap-1.5 rounded-md border px-2 py-2 text-xs transition"
              :class="activeMode === m.mode
                ? 'border-[#4b9e5f] bg-[#4b9e5f]/10 text-[#4b9e5f] dark:border-[#4b9e5f]'
                : 'border-neutral-200 text-neutral-500 hover:bg-neutral-100 dark:border-neutral-800 dark:text-neutral-400 dark:hover:bg-[#24272e]'"
              :title="m.title"
              @click="selectMode(m.mode)"
            >
              <SvgIcon :icon="m.icon" class="text-base" />
              <span class="truncate">{{ m.title }}</span>
            </button>
          </div>
          <NButton dashed block @click="handleAdd">
            {{ $t('chat.newChatButton') }}
          </NButton>
        </div>
        <div class="flex-1 min-h-0 pb-4 overflow-hidden">
          <List />
        </div>
        <div class="flex items-center p-4 space-x-4">
          <div class="flex-1">
            <NButton block @click="show = true">
              {{ $t('store.siderButton') }}
            </NButton>
          </div>
          <NButton @click="handleClearAll">
            <SvgIcon icon="ri:close-circle-line" />
          </NButton>
        </div>
      </main>
      <Footer />
    </div>
  </NLayoutSider>
  <template v-if="isMobile">
    <div v-show="!collapsed" class="fixed inset-0 z-40 w-full h-full bg-black/40" @click="handleUpdateCollapsed" />
  </template>
  <PromptStore v-model:visible="show" />
</template>
