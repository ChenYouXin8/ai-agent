<script setup lang='ts'>
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NLayout,
  NLayoutContent,
  NLayoutSider,
  NScrollbar,
} from 'naive-ui'
import { SvgIcon } from '@/components/common'
import { useAppStore, useWorkspaceStore } from '@/store'
import { useBasicLayout } from '@/hooks/useBasicLayout'
import SiderFooter from './SiderFooter.vue'

const route = useRoute()
const router = useRouter()

const appStore = useAppStore()
const workspaceStore = useWorkspaceStore()
const { isMobile } = useBasicLayout()

const collapsed = computed(() => appStore.siderCollapsed)

const selectedTaskId = computed(() => {
  const t = route.query.task
  return typeof t === 'string' ? t : ''
})

// 侧栏顶部导航
const navItems = [
  { key: 'new', label: '新工作任务', icon: 'ri:edit-2-line' },
]

function handleUpdateCollapsed() {
  appStore.setSiderCollapsed(!collapsed.value)
}

function newTask() {
  if (route.name !== 'Workspace' || route.query.task)
    router.push({ name: 'Workspace' })
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function handleNav(key: string) {
  if (key === 'new')
    newTask()
}

function openTask(taskId: string) {
  if (taskId === selectedTaskId.value && route.name === 'Workspace') {
    if (isMobile.value)
      appStore.setSiderCollapsed(true)
    return
  }
  router.push({ name: 'Workspace', query: { task: taskId } })
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function isRunning(status?: string) {
  return status === 'RUNNING' || status === 'PLANNING' || status === 'QUEUED'
}

function taskDotClass(status?: string) {
  switch (status) {
    case 'RUNNING':
    case 'PLANNING':
    case 'QUEUED':
      return 'bg-emerald-500'
    case 'WAITING_USER':
      return 'bg-amber-500'
    case 'FAILED':
      return 'bg-red-500'
    default:
      return 'bg-neutral-300 dark:bg-neutral-600'
  }
}

let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  workspaceStore.loadTasks()
  // 任务执行中状态会变化，定时刷新侧栏任务列表
  refreshTimer = setInterval(() => {
    workspaceStore.loadTasks()
  }, 6000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})

watch(
  () => route.name,
  () => {
    workspaceStore.loadTasks()
  },
)

watch(isMobile, (val) => {
  appStore.setSiderCollapsed(val)
}, { immediate: true, flush: 'post' })
</script>

<template>
  <div class="h-full bg-[#f7f7f8] text-neutral-900 dark:bg-[#101014] dark:text-neutral-100">
    <NLayout has-sider class="h-full !bg-transparent" :class="{ 'pl-[280px]': !isMobile && !collapsed }">
      <NLayoutSider
        :collapsed="collapsed"
        :collapsed-width="0"
        :width="280"
        :show-trigger="false"
        collapse-mode="transform"
        position="absolute"
        bordered
        :style="{ position: isMobile ? 'fixed' : 'absolute', zIndex: 50 }"
        content-class="!bg-[#f7f7f8] dark:!bg-[#17171c]"
        @update-collapsed="handleUpdateCollapsed"
      >
        <div class="flex flex-col h-full bg-[#f7f7f8] dark:bg-[#17171c]">
          <!-- 顶部品牌 -->
          <div class="flex items-center flex-none px-4 pt-4 pb-1">
            <button class="flex items-center min-w-0 gap-1.5" title="ChenManus" @click="newTask">
              <span class="text-lg font-bold tracking-tight text-neutral-900 truncate dark:text-white">ChenManus</span>
            </button>
          </div>

          <!-- 主导航 -->
          <nav class="flex-none px-2 pt-2">
            <button
              v-for="item in navItems"
              :key="item.key"
              class="flex items-center w-full gap-3.5 px-3 h-10 rounded-lg text-left transition"
              :class="item.key === 'new' && !selectedTaskId
                ? 'bg-white text-neutral-900 shadow-sm dark:bg-[#26262d] dark:text-white'
                : 'text-neutral-700 hover:bg-black/5 dark:text-neutral-200 dark:hover:bg-white/5'"
              @click="handleNav(item.key)"
            >
              <SvgIcon :icon="item.icon" class="text-[19px] text-neutral-500 dark:text-neutral-400" />
              <span class="flex-1 text-[14px] font-medium truncate">{{ item.label }}</span>
            </button>
          </nav>

          <!-- 任务列表 -->
          <div class="flex items-center justify-between flex-none px-5 pt-4 pb-1 text-[12px] text-neutral-400">
            <span>任务</span>
            <span>{{ workspaceStore.recentTasks.length }}</span>
          </div>
          <div class="flex-1 min-h-0">
            <NScrollbar class="h-full">
              <div class="px-2 pb-3">
                <button
                  v-for="task in workspaceStore.recentTasks"
                  :key="task.taskId"
                  class="flex items-center w-full gap-2.5 px-3 h-9 rounded-lg text-left transition"
                  :class="selectedTaskId === task.taskId
                    ? 'bg-white shadow-sm text-neutral-900 dark:bg-[#26262d] dark:text-white'
                    : 'text-neutral-600 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5'"
                  :title="task.title || task.prompt || '未命名任务'"
                  @click="openTask(task.taskId)"
                >
                  <span
                    v-if="isRunning(task.status)"
                    class="flex items-center justify-center flex-none w-4 text-neutral-400"
                  >
                    <SvgIcon icon="ri:loader-4-line" class="text-sm animate-spin" />
                  </span>
                  <span v-else class="flex items-center justify-center flex-none w-4">
                    <span :class="['w-1.5 h-1.5 rounded-full', taskDotClass(task.status)]" />
                  </span>
                  <span class="flex-1 min-w-0">
                    <span class="block text-[13.5px] truncate">{{ task.title || task.prompt || '未命名任务' }}</span>
                  </span>
                </button>

                <div v-if="!workspaceStore.recentTasks.length" class="px-3 py-10 text-center">
                  <div class="mx-auto flex items-center justify-center w-10 h-10 rounded-xl bg-neutral-100 text-neutral-300 dark:bg-neutral-800 dark:text-neutral-600">
                    <SvgIcon icon="ri:inbox-line" class="text-xl" />
                  </div>
                  <div class="mt-3 text-xs text-neutral-400">还没有任务</div>
                  <div class="mt-1 text-[11px] text-neutral-300 dark:text-neutral-600">点击「新工作任务」开始</div>
                </div>
              </div>
            </NScrollbar>
          </div>

          <SiderFooter />
        </div>
      </NLayoutSider>

      <template v-if="isMobile">
        <div v-show="!collapsed" class="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px]" @click="handleUpdateCollapsed" />
      </template>

      <NLayoutContent class="h-full !bg-transparent">
        <RouterView v-slot="{ Component, route: r }">
          <component :is="Component" :key="r.fullPath" />
        </RouterView>
      </NLayoutContent>
    </NLayout>
  </div>
</template>
