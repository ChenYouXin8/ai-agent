<script setup lang='ts'>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NInput,
  NLayout,
  NLayoutContent,
  NLayoutSider,
  NScrollbar,
  NSelect,
  NTag,
  useMessage,
} from 'naive-ui'
import {
  TaskApi,
  loadScope,
  type ChenTask,
  type QuotaView,
  type TaskArtifact,
  type TaskEvent,
  type TaskPriority,
  type TaskScope,
} from '@/api/tasks'
import { SvgIcon } from '@/components/common'
import { useAppStore, useChatStore } from '@/store'
import { useBasicLayout } from '@/hooks/useBasicLayout'
import type { CSSProperties } from 'vue'
import SiderFooter from '@/views/chat/layout/sider/Footer.vue'

const message = useMessage()
const chatStore = useChatStore()
const appStore = useAppStore()
const { isMobile } = useBasicLayout()

const tasks = ref<ChenTask[]>([])
const current = ref<ChenTask | null>(null)
const prompt = ref('')
const priority = ref<TaskPriority>('NORMAL')
const quota = ref<QuotaView | null>(null)
const loading = ref(false)
const events = ref<(TaskEvent & { displayType: string })[]>([])
const scrollRef = ref<HTMLElement | null>(null)
const inputRef = ref<any>(null)

const scope: TaskScope = loadScope()
let eventSource: EventSource | null = null

const priorityOptions = [
  { label: '低优先级', value: 'LOW' },
  { label: '普通', value: 'NORMAL' },
  { label: '高优先级', value: 'HIGH' },
  { label: '紧急', value: 'CRITICAL' },
]

const appItems = [
  { mode: 'workspace', title: 'ChenManus', subtitle: '任务工作区', icon: 'ri:sparkling-2-line', active: true },
  { mode: 'assistant', title: '智能助手', subtitle: '自由对话', icon: 'ri:robot-2-line', active: false },
  { mode: 'love', title: '恋爱大师', subtitle: '情感咨询', icon: 'ri:heart-3-line', active: false },
] as const

const suggestions = [
  { label: '研究主题', icon: 'ri:search-eye-line', prompt: '研究 AI Agent 最近的发展并整理成一份可交付的报告' },
  { label: '分析代码', icon: 'ri:code-box-line', prompt: '分析这个 Java 项目的架构、潜在问题和可以优先修复的地方' },
  { label: '生成方案', icon: 'ri:layout-4-line', prompt: '为一个 SaaS 产品设计从需求到技术落地的完整实施方案' },
  { label: '生产发布', icon: 'ri:rocket-2-line', prompt: '把这个项目部署到生产环境，涉及发布动作时需要人工审批' },
]

const EVENT_NAMES = [
  'task_created', 'task_queued', 'plan_created', 'step_planned', 'step_started',
  'step_retry', 'step_completed', 'step_failed', 'agent_handoff',
  'tool_started', 'tool_completed', 'tool_failed', 'metrics_updated', 'artifact_created',
  'review_started', 'review_completed', 'task_approval_required', 'task_approval_granted',
  'task_approval_rejected', 'task_paused', 'task_resumed', 'task_cancelled',
  'task_completed', 'task_failed', 'task_dead_lettered',
]

const collapsed = computed(() => appStore.siderCollapsed)
const pendingApproval = computed(() => !!current.value && current.value.status === 'WAITING_USER' && hasPendingApproval(current.value))
const steps = computed(() => current.value?.steps ?? [])
const completedSteps = computed(() => steps.value.filter(step => step.status === 'COMPLETED' || step.status === 'SKIPPED').length)
const stepPercent = computed(() => steps.value.length ? Math.round((completedSteps.value / steps.value.length) * 100) : 0)
const currentStep = computed(() => steps.value.find(step => step.status === 'RUNNING') || steps.value.find(step => step.approvalStatus === 'PENDING') || steps.value.find(step => step.status === 'PENDING'))
const currentHeadline = computed(() => {
  if (!current.value) return '把任务交给 ChenManus'
  if (pendingApproval.value) return '需要你的确认'
  if (current.value.status === 'COMPLETED') return '任务已完成'
  if (current.value.status === 'FAILED') return '任务执行失败'
  if (current.value.status === 'REVIEWING') return '正在验收结果'
  return currentStep.value?.title || current.value.title || 'ChenManus 正在工作'
})
const activeTaskId = computed(() => current.value?.taskId || '')
const recentTasks = computed(() => tasks.value.slice(0, 30))

function handleUpdateCollapsed() {
  appStore.setSiderCollapsed(!collapsed.value)
}

function openChatApp(mode: 'assistant' | 'love') {
  chatStore.openApp(mode)
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value)
      scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

function handleEnter(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    createTask()
  }
}

async function loadQuota() {
  try {
    quota.value = await TaskApi.quota(scope)
  }
  catch {
    // legacy 安全模式可能禁止配额接口
  }
}

async function loadTasks() {
  try {
    tasks.value = await TaskApi.list(scope)
  }
  catch (error) {
    console.error('加载任务失败', error)
  }
}

async function refreshTask(id: string) {
  try {
    current.value = await TaskApi.get(scope, id)
  }
  catch (error) {
    console.error('刷新任务失败', error)
  }
}

function pushEvent(event: TaskEvent & { displayType: string }) {
  events.value.unshift(event)
  if (events.value.length > 160)
    events.value.pop()
}

function connectEvents(id: string) {
  eventSource?.close()
  events.value = []
  eventSource = new EventSource(TaskApi.eventsUrl(scope, id))

  EVENT_NAMES.forEach((name) => {
    eventSource?.addEventListener(name, async (e: MessageEvent) => {
      try {
        const payload = JSON.parse(e.data) as TaskEvent
        pushEvent({ ...payload, displayType: name })
        await refreshTask(id)
        await loadTasks()
        await loadQuota()
        scrollToBottom()
      }
      catch (error) {
        console.error('处理 Agent 事件失败', error)
      }
    })
  })

  eventSource.onerror = () => {
    // 浏览器会自动重连
  }
}

async function openTask(id: string) {
  await refreshTask(id)
  connectEvents(id)
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function newTask() {
  eventSource?.close()
  current.value = null
  events.value = []
  prompt.value = ''
  nextTick(() => inputRef.value?.focus?.())
}

async function createTask() {
  const value = prompt.value.trim()
  if (!value || loading.value)
    return

  loading.value = true
  try {
    const task = await TaskApi.create(scope, value, priority.value)
    prompt.value = ''
    await Promise.all([loadTasks(), loadQuota()])
    await openTask(task.taskId)
  }
  catch (error: any) {
    console.error('创建任务失败', error)
    message.error(error?.message || '创建任务失败')
  }
  finally {
    loading.value = false
  }
}

async function runAction(name: 'pause' | 'resume' | 'cancel') {
  if (!current.value)
    return
  try {
    await TaskApi.action(scope, current.value.taskId, name)
    await Promise.all([refreshTask(current.value.taskId), loadTasks(), loadQuota()])
  }
  catch (error: any) {
    message.error(error?.message || '操作失败')
  }
}

async function approveTask() {
  if (!current.value)
    return
  try {
    await TaskApi.approve(scope, current.value.taskId, 'ChenManus 工作区人工批准')
    await Promise.all([refreshTask(current.value.taskId), loadTasks(), loadQuota()])
  }
  catch (error: any) {
    message.error(error?.message || '审批失败')
  }
}

async function rejectTask() {
  if (!current.value)
    return
  const note = window.prompt('请输入驳回原因（可选）', '') ?? ''
  try {
    await TaskApi.reject(scope, current.value.taskId, note)
    await Promise.all([refreshTask(current.value.taskId), loadTasks(), loadQuota()])
  }
  catch (error: any) {
    message.error(error?.message || '驳回失败')
  }
}

function hasPendingApproval(task: ChenTask | null) {
  return !!task?.steps?.some(step => step.approvalStatus === 'PENDING')
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    CREATED: '已创建',
    QUEUED: '排队中',
    PLANNING: '规划中',
    RUNNING: '执行中',
    PAUSED: '已暂停',
    WAITING_USER: '等待确认',
    REVIEWING: '审核中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[status || ''] || status || ''
}

function statusTagType(status?: string) {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'error'
    case 'WAITING_USER':
      return 'warning'
    case 'RUNNING':
    case 'PLANNING':
    case 'QUEUED':
      return 'info'
    default:
      return 'default'
  }
}

function dotClass(status?: string) {
  switch (status) {
    case 'RUNNING':
    case 'PLANNING':
    case 'QUEUED':
      return 'bg-emerald-500'
    case 'WAITING_USER':
      return 'bg-amber-500'
    case 'COMPLETED':
      return 'bg-neutral-300'
    case 'FAILED':
      return 'bg-red-500'
    default:
      return 'bg-neutral-300'
  }
}

function stepIcon(step: any) {
  if (step.status === 'COMPLETED' || step.status === 'SKIPPED')
    return 'ri:check-line'
  if (step.status === 'FAILED')
    return 'ri:close-line'
  if (step.approvalStatus === 'PENDING')
    return 'ri:shield-check-line'
  if (step.status === 'RUNNING')
    return 'ri:loader-4-line'
  return step.parallelizable ? 'ri:git-merge-line' : 'ri:checkbox-blank-circle-line'
}

function eventLabel(type: string) {
  const map: Record<string, string> = {
    task_created: 'TASK', task_queued: 'QUEUE', plan_created: 'PLAN', step_planned: 'PLAN STEP',
    step_started: 'STEP', step_retry: 'RETRY', step_completed: 'STEP OK', step_failed: 'STEP ERROR',
    agent_handoff: 'HANDOFF', tool_started: 'TOOL START', tool_completed: 'TOOL OK', tool_failed: 'TOOL ERROR',
    metrics_updated: 'METRICS', artifact_created: 'ARTIFACT', review_started: 'REVIEW',
    review_completed: 'REVIEW OK', task_approval_required: 'APPROVAL', task_approval_granted: 'APPROVED',
    task_approval_rejected: 'REJECTED', task_paused: 'PAUSE', task_resumed: 'RESUME',
    task_cancelled: 'CANCEL', task_completed: 'DONE', task_failed: 'FAILED', task_dead_lettered: 'DLQ',
  }
  return map[type] || type
}

function formatDuration(ms?: number) {
  if (!ms)
    return '—'
  if (ms < 1000)
    return \`\${ms} ms\`
  const seconds = Math.round(ms / 100) / 10
  if (seconds < 60)
    return \`\${seconds} s\`
  return \`\${Math.floor(seconds / 60)}m \${Math.round(seconds % 60)}s\`
}

function displayTokens(actual?: number, estimated?: number) {
  if (actual && actual > 0)
    return String(actual)
  return \`~\${estimated || 0}\`
}

function canPreview(artifact: TaskArtifact) {
  return artifact?.mediaType === 'application/pdf'
    || artifact?.mediaType?.startsWith('image/')
    || artifact?.mediaType?.startsWith('text/')
}

function artifactUrl(artifact: TaskArtifact, preview = false) {
  if (!current.value)
    return '#'
  return TaskApi.artifactUrl(scope, current.value.taskId, artifact.artifactId, preview)
}

watch(isMobile, (val) => {
  appStore.setSiderCollapsed(val)
}, { immediate: true, flush: 'post' })

onMounted(() => {
  loadTasks()
  loadQuota()
  if (!isMobile.value)
    inputRef.value?.focus?.()
})

onBeforeUnmount(() => eventSource?.close())
</script>

<template>
  <div class="h-full bg-[#f7f7f8] text-neutral-900 dark:bg-[#101014] dark:text-neutral-100">
    <NLayout has-sider class="h-full bg-transparent">
      <NLayoutSider
        :collapsed="collapsed"
        :collapsed-width="0"
        :width="304"
        :show-trigger="isMobile ? false : 'arrow-circle'"
        collapse-mode="transform"
        position="absolute"
        bordered
        :style="({ position: isMobile ? 'fixed' : 'absolute', zIndex: 50 } as CSSProperties)"
        content-class="!bg-[#f7f7f8] dark:!bg-[#17171c]"
        @update-collapsed="handleUpdateCollapsed"
      >
        <div class="flex flex-col h-full border-r border-neutral-200/80 bg-[#f7f7f8] dark:border-neutral-800 dark:bg-[#17171c]">
          <div class="px-3 pt-3 pb-2">
            <div class="flex items-center gap-2 px-2 pb-3">
              <div class="flex items-center justify-center w-8 h-8 text-white rounded-xl bg-neutral-900 dark:bg-white dark:text-neutral-900">
                <SvgIcon icon="ri:sparkling-2-line" class="text-lg" />
              </div>
              <div class="min-w-0">
                <div class="text-sm font-semibold truncate">ChenYouXin</div>
                <div class="text-[11px] text-neutral-400">AI Agent Workspace</div>
              </div>
            </div>

            <button
              class="flex items-center w-full gap-3 px-3 py-2.5 rounded-xl bg-white border border-neutral-200 shadow-sm transition hover:border-neutral-300 hover:shadow dark:bg-[#222228] dark:border-neutral-800 dark:hover:border-neutral-700"
              @click="newTask"
            >
              <span class="flex items-center justify-center w-7 h-7 rounded-lg bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-200">
                <SvgIcon icon="ri:add-line" />
              </span>
              <span class="flex-1 text-sm font-medium text-left">新建任务</span>
              <span class="text-[10px] text-neutral-400">Ctrl N</span>
            </button>
          </div>

          <div class="px-3 pt-1">
            <div class="px-2 pb-2 text-[11px] font-medium tracking-wide text-neutral-400 uppercase">应用</div>
            <div class="space-y-1.5">
              <button
                v-for="app in appItems"
                :key="app.mode"
                class="flex items-center w-full gap-3 px-3 py-2.5 text-left rounded-xl transition"
                :class="app.active
                  ? 'bg-white text-neutral-900 shadow-sm border border-neutral-200 dark:bg-[#26262d] dark:text-white dark:border-neutral-800'
                  : 'text-neutral-500 hover:bg-white/80 hover:text-neutral-900 dark:text-neutral-400 dark:hover:bg-[#222228] dark:hover:text-neutral-200'"
                @click="app.mode === 'assistant' ? openChatApp('assistant') : app.mode === 'love' ? openChatApp('love') : undefined"
              >
                <span
                  class="flex items-center justify-center flex-none w-8 h-8 rounded-lg"
                  :class="app.active ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-neutral-100/80 text-neutral-400 dark:bg-neutral-800 dark:text-neutral-500'"
                >
                  <SvgIcon :icon="app.icon" class="text-base" />
                </span>
                <span class="flex-1 min-w-0">
                  <span class="block text-sm font-medium truncate">{{ app.title }}</span>
                  <span class="block mt-0.5 text-[11px] text-neutral-400 truncate">{{ app.subtitle }}</span>
                </span>
                <span v-if="app.active" class="w-1.5 h-1.5 rounded-full bg-emerald-500" />
              </button>
            </div>
          </div>

          <div class="flex items-center justify-between px-5 pt-6 pb-2">
            <div class="text-[11px] font-medium tracking-wide text-neutral-400 uppercase">任务</div>
            <span class="text-[11px] text-neutral-400">{{ recentTasks.length }}</span>
          </div>

          <div class="flex-1 min-h-0 overflow-hidden">
            <NScrollbar class="px-3">
              <div class="pb-4 space-y-1">
                <button
                  v-for="task in recentTasks"
                  :key="task.taskId"
                  class="flex items-center w-full gap-2.5 px-3 py-2.5 text-left rounded-xl transition"
                  :class="activeTaskId === task.taskId
                    ? 'bg-white shadow-sm text-neutral-900 border border-neutral-200 dark:bg-[#26262d] dark:text-white dark:border-neutral-800'
                    : 'text-neutral-500 hover:bg-white/80 dark:text-neutral-400 dark:hover:bg-[#222228]'"
                  @click="openTask(task.taskId)"
                >
                  <span :class="['w-2 h-2 rounded-full flex-none', dotClass(task.status)]" />
                  <span class="flex-1 min-w-0">
                    <span class="block text-[13px] truncate">{{ task.title || task.prompt || '未命名任务' }}</span>
                    <span class="block mt-0.5 text-[10px] text-neutral-400 truncate">{{ statusLabel(task.status) }}</span>
                  </span>
                </button>

                <div v-if="!recentTasks.length" class="px-3 py-10 text-center">
                  <div class="mx-auto flex items-center justify-center w-10 h-10 rounded-xl bg-neutral-100 text-neutral-300 dark:bg-neutral-800 dark:text-neutral-600">
                    <SvgIcon icon="ri:inbox-line" class="text-xl" />
                  </div>
                  <div class="mt-3 text-xs text-neutral-400">还没有任务</div>
                  <div class="mt-1 text-[11px] text-neutral-300 dark:text-neutral-600">从上面创建第一个任务</div>
                </div>
              </div>
            </NScrollbar>
          </div>

          <div class="px-4 pb-2">
            <div v-if="quota" class="px-2 py-2 text-[10px] text-neutral-400 truncate">
              租户任务 {{ quota.activeTasks }}/{{ quota.maxActiveTasksPerTenant || '∞' }}
            </div>
          </div>
          <SiderFooter />
        </div>
      </NLayoutSider>

      <template v-if="isMobile">
        <div v-show="!collapsed" class="fixed inset-0 z-40 bg-black/40 backdrop-blur-[1px]" @click="handleUpdateCollapsed" />
      </template>

      <NLayoutContent class="h-full bg-transparent">
        <div class="flex flex-col h-full min-w-0">
          <header class="flex items-center justify-between flex-none h-14 px-3 border-b border-neutral-200/80 bg-[#f7f7f8]/90 backdrop-blur dark:border-neutral-800 dark:bg-[#101014]/90">
            <div class="flex items-center min-w-0 gap-2">
              <button
                class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-neutral-200/70 dark:text-neutral-300 dark:hover:bg-neutral-800"
                @click="handleUpdateCollapsed"
              >
                <SvgIcon :icon="collapsed ? 'ri:menu-line' : 'ri:sidebar-fold-line'" class="text-lg" />
              </button>
              <div class="min-w-0">
                <div class="text-sm font-semibold truncate">
                  {{ current?.title || 'ChenManus 工作区' }}
                </div>
                <div class="flex items-center gap-2 mt-0.5">
                  <span class="text-[10px] text-neutral-400">任务型 Agent Runtime</span>
                  <NTag v-if="current" size="tiny" :type="statusTagType(current.status)" round :bordered="false">
                    {{ statusLabel(current.status) }}
                  </NTag>
                </div>
              </div>
            </div>

            <div class="flex items-center gap-1">
              <button class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-neutral-200/70 dark:text-neutral-300 dark:hover:bg-neutral-800" title="新任务" @click="newTask">
                <SvgIcon icon="ri:add-line" class="text-lg" />
              </button>
            </div>
          </header>

          <main class="flex-1 min-h-0 overflow-hidden">
            <div ref="scrollRef" class="h-full overflow-y-auto">
              <div v-if="!current" class="flex flex-col items-center max-w-4xl min-h-full px-5 pt-16 pb-8 mx-auto text-center sm:pt-24">
                <div class="flex items-center justify-center w-14 h-14 mb-6 rounded-2xl bg-neutral-900 text-white shadow-sm dark:bg-white dark:text-neutral-900">
                  <SvgIcon icon="ri:sparkling-2-line" class="text-3xl" />
                </div>
                <h1 class="text-3xl font-semibold tracking-tight text-neutral-900 dark:text-white sm:text-4xl">
                  {{ currentHeadline }}
                </h1>
                <p class="max-w-2xl mt-4 text-sm leading-6 text-neutral-500 dark:text-neutral-400">
                  用一句自然语言描述你要完成的事情。ChenManus 会先规划，再调度多个 Agent 执行、校验和交付；涉及高风险动作时会停下来等你确认。
                </p>

                <div class="grid w-full grid-cols-1 gap-3 mt-10 sm:grid-cols-2">
                  <button
                    v-for="item in suggestions"
                    :key="item.label"
                    class="flex items-start gap-3 p-4 text-left bg-white border rounded-2xl border-neutral-200/80 hover:border-neutral-300 hover:shadow-sm dark:bg-[#17171c] dark:border-neutral-800 dark:hover:border-neutral-700"
                    @click="prompt = item.prompt; nextTick(() => inputRef.value?.focus?.())"
                  >
                    <span class="flex items-center justify-center flex-none w-9 h-9 rounded-xl bg-neutral-100 text-neutral-500 dark:bg-neutral-800 dark:text-neutral-300">
                      <SvgIcon :icon="item.icon" class="text-base" />
                    </span>
                    <span>
                      <span class="block text-sm font-medium text-neutral-700 dark:text-neutral-200">{{ item.label }}</span>
                      <span class="block mt-1 text-xs leading-5 text-neutral-400">{{ item.prompt }}</span>
                    </span>
                  </button>
                </div>

                <div class="flex flex-wrap justify-center gap-6 mt-10 text-[11px] text-neutral-400">
                  <span class="inline-flex items-center gap-1.5"><SvgIcon icon="ri:node-tree" /> DAG 规划</span>
                  <span class="inline-flex items-center gap-1.5"><SvgIcon icon="ri:team-line" /> Team Agent</span>
                  <span class="inline-flex items-center gap-1.5"><SvgIcon icon="ri:shield-check-line" /> Human Approval</span>
                  <span class="inline-flex items-center gap-1.5"><SvgIcon icon="ri:check-double-line" /> Reviewer</span>
                </div>
              </div>

              <div v-else class="w-full max-w-5xl px-4 pt-8 pb-10 mx-auto sm:px-8">
                <div class="max-w-3xl mx-auto">
                  <div class="flex items-start gap-3">
                    <div class="flex items-center justify-center flex-none w-9 h-9 rounded-xl bg-neutral-900 text-white dark:bg-white dark:text-neutral-900">
                      <SvgIcon icon="ri:sparkling-2-line" class="text-base" />
                    </div>
                    <div class="flex-1 min-w-0">
                      <div class="text-sm font-medium text-neutral-500 dark:text-neutral-400">你的任务</div>
                      <div class="mt-1 text-lg leading-7 text-neutral-900 dark:text-white">{{ current.prompt }}</div>
                    </div>
                  </div>

                  <div class="my-8 border-l-2 border-neutral-200 dark:border-neutral-800">
                    <div class="pl-6">
                      <div class="flex flex-wrap items-center gap-2">
                        <div class="text-base font-semibold text-neutral-900 dark:text-white">{{ currentHeadline }}</div>
                        <NTag v-if="current" size="small" :type="statusTagType(current.status)" round :bordered="false">
                          {{ statusLabel(current.status) }}
                        </NTag>
                        <span v-if="quota" class="text-[11px] text-neutral-400">
                          {{ quota.activeTasks }}/{{ quota.maxActiveTasksPerTenant || '∞' }} active
                        </span>
                      </div>

                      <p v-if="current.planSummary" class="mt-3 text-sm leading-6 text-neutral-500 dark:text-neutral-400">
                        {{ current.planSummary }}
                      </p>

                      <div v-if="steps.length" class="p-4 mt-5 border rounded-2xl bg-white/70 border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                        <div class="flex items-center justify-between gap-3">
                          <div>
                            <div class="text-xs font-medium text-neutral-500">执行进度</div>
                            <div class="mt-1 text-sm text-neutral-700 dark:text-neutral-200">
                              {{ completedSteps }} / {{ steps.length }} steps
                              <span v-if="currentStep" class="ml-2 text-neutral-400">· {{ currentStep.title }}</span>
                            </div>
                          </div>
                          <div class="text-sm font-semibold tabular-nums text-neutral-700 dark:text-neutral-200">{{ stepPercent }}%</div>
                        </div>
                        <div class="w-full h-1.5 mt-3 overflow-hidden rounded-full bg-neutral-100 dark:bg-neutral-800">
                          <div class="h-full transition-all duration-500 rounded-full bg-neutral-900 dark:bg-white" :style="{ width: stepPercent + '%' }" />
                        </div>

                        <div class="mt-4 space-y-1.5">
                          <div
                            v-for="step in steps"
                            :key="step.stepId"
                            class="flex items-center gap-3 px-2.5 py-2.5 rounded-xl"
                            :class="step.approvalStatus === 'PENDING' ? 'bg-amber-50 dark:bg-amber-500/10' : 'bg-transparent'"
                          >
                            <span class="flex items-center justify-center flex-none w-6 h-6 rounded-lg bg-neutral-100 text-neutral-500 dark:bg-neutral-800 dark:text-neutral-400">
                              <SvgIcon :icon="stepIcon(step)" :class="step.status === 'RUNNING' ? 'animate-spin' : ''" />
                            </span>
                            <span class="flex-1 min-w-0">
                              <span class="block text-sm truncate text-neutral-700 dark:text-neutral-200">{{ step.title }}</span>
                              <span class="block mt-0.5 text-[10px] text-neutral-400 truncate">
                                {{ step.approvalStatus === 'PENDING' ? '等待人工确认' : step.status === 'RUNNING' ? '正在执行' : step.status === 'COMPLETED' ? '已完成' : step.status || '待执行' }}
                              </span>
                            </span>
                            <span v-if="step.parallelizable" class="text-[10px] text-neutral-400">并行</span>
                            <span v-if="step.retryCount" class="text-[10px] text-neutral-400">retry {{ step.retryCount }}</span>
                          </div>
                        </div>
                      </div>

                      <div v-if="pendingApproval" class="flex items-start gap-3 p-4 mt-4 border rounded-2xl border-amber-200 bg-amber-50 dark:border-amber-500/20 dark:bg-amber-500/10">
                        <span class="flex items-center justify-center flex-none w-9 h-9 rounded-xl bg-white text-amber-600 dark:bg-amber-500/10 dark:text-amber-400">
                          <SvgIcon icon="ri:shield-check-line" class="text-lg" />
                        </span>
                        <div class="flex-1">
                          <div class="text-sm font-medium text-amber-800 dark:text-amber-300">需要人工确认后继续</div>
                          <div class="mt-1 text-xs leading-5 text-amber-700/80 dark:text-amber-200/70">
                            ChenManus 已停在需要人类确认的执行节点。批准后才会继续进入队列。
                          </div>
                        </div>
                        <div class="flex items-center flex-none gap-2">
                          <NButton size="small" type="warning" @click="approveTask">批准</NButton>
                          <NButton size="small" secondary @click="rejectTask">驳回</NButton>
                        </div>
                      </div>

                      <div class="grid grid-cols-2 gap-3 mt-4 sm:grid-cols-4">
                        <div class="p-3.5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                          <div class="text-[10px] text-neutral-400">耗时</div>
                          <div class="mt-1 text-sm font-semibold text-neutral-800 dark:text-neutral-100">{{ formatDuration(current.durationMs) }}</div>
                        </div>
                        <div class="p-3.5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                          <div class="text-[10px] text-neutral-400">输入</div>
                          <div class="mt-1 text-sm font-semibold text-neutral-800 dark:text-neutral-100">{{ displayTokens(current.actualInputTokens, current.estimatedInputTokens) }} <span class="text-[10px] font-normal text-neutral-400">tokens</span></div>
                        </div>
                        <div class="p-3.5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                          <div class="text-[10px] text-neutral-400">输出</div>
                          <div class="mt-1 text-sm font-semibold text-neutral-800 dark:text-neutral-100">{{ displayTokens(current.actualOutputTokens, current.estimatedOutputTokens) }} <span class="text-[10px] font-normal text-neutral-400">tokens</span></div>
                        </div>
                        <div class="p-3.5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                          <div class="text-[10px] text-neutral-400">模型调用</div>
                          <div class="mt-1 text-sm font-semibold text-neutral-800 dark:text-neutral-100">{{ current.modelCallCount || 0 }} <span class="text-[10px] font-normal text-neutral-400">calls</span></div>
                        </div>
                      </div>

                      <section v-if="current.review" class="p-5 mt-5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                        <div class="flex items-center justify-between gap-3">
                          <div>
                            <div class="text-sm font-semibold text-neutral-800 dark:text-neutral-100">Reviewer</div>
                            <div class="mt-1 text-xs text-neutral-400">最终结果验收</div>
                          </div>
                          <NTag size="small" :type="current.review.passed ? 'success' : 'warning'" round :bordered="false">
                            {{ current.review.passed ? 'PASS' : 'NEEDS REPAIR' }}
                          </NTag>
                        </div>
                        <p class="mt-4 text-sm leading-6 text-neutral-500 dark:text-neutral-400">{{ current.review.feedback }}</p>
                        <p v-if="current.review.missingItems" class="mt-2 text-xs text-amber-600 dark:text-amber-400">缺失项：{{ current.review.missingItems }}</p>
                      </section>

                      <section v-if="current.artifacts?.length" class="p-5 mt-5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                        <div class="flex items-center justify-between">
                          <div>
                            <div class="text-sm font-semibold text-neutral-800 dark:text-neutral-100">交付产物</div>
                            <div class="mt-1 text-xs text-neutral-400">{{ current.artifacts.length }} 个产物</div>
                          </div>
                          <SvgIcon icon="ri:file-3-line" class="text-lg text-neutral-400" />
                        </div>
                        <div class="mt-4 space-y-2">
                          <div
                            v-for="artifact in current.artifacts"
                            :key="artifact.artifactId"
                            class="flex items-center gap-3 p-3 rounded-xl bg-neutral-50 dark:bg-neutral-900"
                          >
                            <div class="flex items-center justify-center flex-none w-9 h-9 rounded-lg bg-white border border-neutral-200 text-neutral-500 dark:bg-neutral-800 dark:border-neutral-700">
                              <SvgIcon :icon="artifact.type === 'PDF' || artifact.mediaType === 'application/pdf' ? 'ri:file-pdf-2-line' : 'ri:file-3-line'" />
                            </div>
                            <div class="flex-1 min-w-0">
                              <div class="text-sm truncate text-neutral-700 dark:text-neutral-200">{{ artifact.name }}</div>
                              <div class="mt-0.5 text-[10px] text-neutral-400 truncate">{{ artifact.path }}</div>
                            </div>
                            <div class="flex items-center gap-2">
                              <a v-if="canPreview(artifact)" :href="artifactUrl(artifact, true)" target="_blank" rel="noreferrer" class="text-xs text-neutral-500 hover:text-neutral-900 dark:hover:text-white">预览</a>
                              <a :href="artifactUrl(artifact)" target="_blank" rel="noreferrer" class="text-xs text-neutral-500 hover:text-neutral-900 dark:hover:text-white">下载</a>
                            </div>
                          </div>
                        </div>
                      </section>

                      <section v-if="current.result" class="p-5 mt-5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                        <div class="flex items-center justify-between">
                          <div>
                            <div class="text-sm font-semibold text-neutral-800 dark:text-neutral-100">最终结果</div>
                            <div class="mt-1 text-xs text-neutral-400">ChenManus 交付内容</div>
                          </div>
                          <SvgIcon icon="ri:check-double-line" class="text-lg text-emerald-500" />
                        </div>
                        <pre class="mt-4 p-4 overflow-auto text-sm leading-6 whitespace-pre-wrap rounded-xl bg-neutral-50 dark:bg-neutral-900 text-neutral-700 dark:text-neutral-200 max-h-[32rem]">{{ current.result }}</pre>
                      </section>

                      <section v-if="events.length" class="p-5 mt-5 bg-white border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                        <div class="flex items-center justify-between">
                          <div>
                            <div class="text-sm font-semibold text-neutral-800 dark:text-neutral-100">活动日志</div>
                            <div class="mt-1 text-xs text-neutral-400">实时事件与持久化审计</div>
                          </div>
                          <span class="text-[10px] text-neutral-400">{{ events.length }} events</span>
                        </div>
                        <div class="mt-4 space-y-3">
                          <div v-for="(event, index) in events.slice(0, 40)" :key="\`\${event.eventId || event.timestamp}-\${index}\`" class="flex gap-3">
                            <div class="flex flex-col items-center flex-none">
                              <span class="w-2 h-2 mt-1.5 rounded-full bg-neutral-300 dark:bg-neutral-700" />
                              <span v-if="index < events.slice(0, 40).length - 1" class="w-px flex-1 mt-1 bg-neutral-200 dark:bg-neutral-800" />
                            </div>
                            <div class="flex-1 min-w-0 pb-2">
                              <div class="flex items-center gap-2">
                                <span class="text-[10px] font-medium tracking-wide text-neutral-400 uppercase">{{ eventLabel(event.displayType) }}</span>
                                <span class="text-[10px] text-neutral-300">{{ event.timestamp ? new Date(event.timestamp).toLocaleTimeString() : '' }}</span>
                              </div>
                              <p class="mt-1 text-xs leading-5 whitespace-pre-wrap text-neutral-500 dark:text-neutral-400">{{ event.message }}</p>
                            </div>
                          </div>
                        </div>
                      </section>

                      <div class="flex flex-wrap items-center gap-2 mt-5">
                        <NButton v-if="['RUNNING', 'PLANNING', 'QUEUED'].includes(current.status || '')" size="small" secondary @click="runAction('pause')">暂停</NButton>
                        <NButton v-if="current.status === 'PAUSED'" size="small" secondary @click="runAction('resume')">继续</NButton>
                        <NButton v-if="!['COMPLETED', 'FAILED', 'CANCELLED'].includes(current.status || '')" size="small" quaternary @click="runAction('cancel')">取消任务</NButton>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </main>

          <footer class="flex-none px-3 pb-4 pt-2 sm:px-6">
            <div class="w-full max-w-4xl mx-auto">
              <div class="p-2 border rounded-2xl bg-white/95 border-neutral-200 shadow-[0_4px_20px_rgba(0,0,0,0.06)] dark:bg-[#17171c] dark:border-neutral-800">
                <NInput
                  ref="inputRef"
                  v-model:value="prompt"
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: isMobile ? 5 : 8 }"
                  :disabled="loading"
                  :placeholder="current ? '继续告诉 ChenManus 你希望怎么处理……' : '描述一个你希望 ChenManus 完成的任务……'"
                  :bordered="false"
                  class="workspace-composer"
                  @keypress="handleEnter"
                />
                <div class="flex items-center justify-between gap-2 px-1 pt-1">
                  <div class="flex items-center gap-1.5">
                    <NSelect v-model:value="priority" :options="priorityOptions" size="tiny" class="!w-24" :disabled="loading" />
                    <span class="hidden text-[10px] text-neutral-400 sm:inline">Enter 发送 · Shift+Enter 换行</span>
                  </div>
                  <NButton
                    type="primary"
                    circle
                    :disabled="loading || !prompt.trim()"
                    :loading="loading"
                    @click="createTask"
                  >
                    <template #icon><SvgIcon icon="ri:arrow-up-line" /></template>
                  </NButton>
                </div>
              </div>
              <div class="pt-2 text-center text-[10px] text-neutral-400">
                ChenManus 会自主规划与执行，涉及高风险操作时会请求你的确认。
              </div>
            </div>
          </footer>
        </div>
      </NLayoutContent>
    </NLayout>
  </div>
</template>

<style scoped>
:deep(.workspace-composer .n-input__textarea-el) {
  min-height: 44px;
  padding: 10px 12px 4px;
  font-size: 14px;
  line-height: 1.6;
}

:deep(.workspace-composer .n-input__textarea-el::placeholder) {
  color: #a3a3a3;
}

.dark :deep(.workspace-composer .n-input__textarea-el::placeholder) {
  color: #68686f;
}
</style>
