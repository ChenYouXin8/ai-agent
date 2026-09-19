<script setup lang='ts'>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NInput,
  NSelect,
  NTag,
  useMessage,
} from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import {
  TaskApi,
  type ChenTask,
  type QuotaView,
  type TaskArtifact,
  type TaskEvent,
  type TaskPriority,
} from '@/api/tasks'
import { SvgIcon } from '@/components/common'
import { useAppStore, useWorkspaceStore } from '@/store'
import { useBasicLayout } from '@/hooks/useBasicLayout'

const message = useMessage()
const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const workspaceStore = useWorkspaceStore()
const { isMobile } = useBasicLayout()

const current = ref<ChenTask | null>(null)
const prompt = ref('')
const priority = ref<TaskPriority>('NORMAL')
const quota = ref<QuotaView | null>(null)
const loading = ref(false)
const eventFilter = ref<EventFilter>('ALL')
const events = ref<(TaskEvent & { displayType: string })[]>([])
const historyEvents = ref<(TaskEvent & { displayType: string })[]>([])
const scrollRef = ref<HTMLElement | null>(null)
const inputRef = ref<any>(null)

const scope = workspaceStore.scope
let eventSource: EventSource | null = null

const priorityOptions = [
  { label: '低优先级', value: 'LOW' },
  { label: '普通', value: 'NORMAL' },
  { label: '高优先级', value: 'HIGH' },
  { label: '紧急', value: 'CRITICAL' },
]

const suggestions = [
  { label: '研究主题', icon: 'ri:search-eye-line', prompt: '研究 AI Agent 最近的发展并整理成一份可交付的报告' },
  { label: '分析代码', icon: 'ri:code-box-line', prompt: '分析这个 Java 项目的架构、潜在问题和可以优先修复的地方' },
  { label: '生成方案', icon: 'ri:layout-4-line', prompt: '为一个 SaaS 产品设计从需求到技术落地的完整实施方案' },
  { label: '生产发布', icon: 'ri:rocket-2-line', prompt: '把这个项目部署到生产环境，涉及发布动作时需要人工审批' },
]

const EVENT_NAMES = [
  'task_created', 'task_queued', 'message', 'plan_created', 'step_planned', 'step_started',
  'step_retry', 'step_completed', 'step_failed', 'agent_handoff',
  'tool_started', 'tool_completed', 'tool_failed', 'metrics_updated', 'artifact_created',
  'review_started', 'review_completed', 'task_approval_required', 'task_approval_granted',
  'task_approval_rejected', 'task_paused', 'task_resumed', 'task_cancelled',
  'task_completed', 'task_failed', 'task_dead_lettered',
]

const EVENT_FILTERS = [
  { label: '全部事件', value: 'ALL' },
  { label: '任务状态', value: 'TASK' },
  { label: '执行步骤', value: 'STEP' },
  { label: 'Agent / 工具', value: 'AGENT' },
  { label: '审核 / 审批', value: 'REVIEW' },
  { label: '产物 / 指标', value: 'RESOURCE' },
] as const

type EventFilter = typeof EVENT_FILTERS[number]['value']

// NSelect 的 options 需要可变数组，这里从只读常量派生一份可变副本
const EVENT_FILTER_OPTIONS = EVENT_FILTERS.map((f) => ({ label: f.label, value: f.value }))

const collapsed = computed(() => appStore.siderCollapsed)
const pendingApproval = computed(() => !!current.value && current.value.status === 'WAITING_USER' && hasPendingApproval(current.value))
const steps = computed(() => current.value?.steps ?? [])
const completedSteps = computed(() => steps.value.filter(step => step.status === 'COMPLETED' || step.status === 'SKIPPED').length)
const stepPercent = computed(() => steps.value.length ? Math.round((completedSteps.value / steps.value.length) * 100) : 0)
const currentStep = computed(() => steps.value.find(step => step.status === 'RUNNING') || steps.value.find(step => step.approvalStatus === 'PENDING') || steps.value.find(step => step.status === 'PENDING'))
const isTaskRunning = computed(() => ['RUNNING', 'PLANNING', 'QUEUED'].includes(current.value?.status || ''))
const currentHeadline = computed(() => {
  if (!current.value)
    return '把任务交给 ChenManus'
  if (pendingApproval.value)
    return '需要你的确认'
  if (current.value.status === 'COMPLETED')
    return '任务已完成'
  if (current.value.status === 'FAILED')
    return '任务执行失败'
  if (current.value.status === 'REVIEWING')
    return '正在验收结果'
  return currentStep.value?.title || current.value.title || 'ChenManus 正在工作'
})
// 实时事件 + 历史事件合并去重，按时间倒序（最新在前）
const allEvents = computed(() => {
  const map = new Map<string, TaskEvent & { displayType: string }>()
  const put = (e: TaskEvent & { displayType: string }) => {
    const key = e.eventId || `${e.timestamp || ''}-${e.displayType}-${e.message || ''}`
    if (!map.has(key))
      map.set(key, e)
  }
  // 历史事件在前（时间正序），实时事件覆盖同 key
  historyEvents.value.forEach(put)
  events.value.forEach(put)
  return [...map.values()].sort((a, b) => Number(new Date(b.timestamp as any)) - Number(new Date(a.timestamp as any)))
})
const visibleEvents = computed(() => {
  const filter = eventFilter.value
  if (filter === 'ALL')
    return allEvents.value
  const groups: Record<Exclude<EventFilter, 'ALL'>, string[]> = {
    TASK: ['task_created', 'task_queued', 'task_paused', 'task_resumed', 'task_cancelled', 'task_completed', 'task_failed', 'task_dead_lettered', 'message'],
    STEP: ['plan_created', 'step_planned', 'step_started', 'step_retry', 'step_completed', 'step_failed'],
    AGENT: ['agent_handoff', 'tool_started', 'tool_completed', 'tool_failed'],
    REVIEW: ['review_started', 'review_completed', 'task_approval_required', 'task_approval_granted', 'task_approval_rejected'],
    RESOURCE: ['metrics_updated', 'artifact_created'],
  }
  return allEvents.value.filter(event => groups[filter].includes(event.displayType))
})

function handleUpdateCollapsed() {
  appStore.setSiderCollapsed(!collapsed.value)
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

function handleGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'n') {
    e.preventDefault()
    newTask()
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
  await workspaceStore.loadTasks()
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
  historyEvents.value = []
  eventSource = new EventSource(TaskApi.eventsUrl(scope, id))

  EVENT_NAMES.forEach((name) => {
    eventSource?.addEventListener(name, (e: MessageEvent) => {
      try {
        const payload = JSON.parse(e.data) as TaskEvent
        pushEvent({ ...payload, displayType: name })
        // SSE 连接时会回放历史事件，批量到达时合并刷新，避免每个事件都打 3 个接口
        scheduleRefresh(id)
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

// 合并高频事件触发的任务/列表/配额刷新（200ms 内只打一轮）
let refreshTimer: ReturnType<typeof setTimeout> | null = null
function scheduleRefresh(id: string) {
  if (refreshTimer)
    clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => {
    refreshTimer = null
    if (current.value?.taskId !== id)
      return
    Promise.all([refreshTask(id), loadTasks(), loadQuota()]).catch((error) => {
      console.error('刷新任务状态失败', error)
    })
  }, 200)
}

async function openTask(id: string) {
  await refreshTask(id)
  connectEvents(id)
  // SSE 只推送连接后的实时事件，历史事件单独拉取，保证回看/刷新后日志完整
  try {
    const history = await TaskApi.eventHistory(scope, id)
    historyEvents.value = (history || []).map(e => ({
      ...e,
      displayType: (e.type || '').toLowerCase(),
    }))
  }
  catch (error) {
    console.error('加载事件历史失败', error)
  }
  if (isMobile.value)
    appStore.setSiderCollapsed(true)
}

function newTask() {
  eventSource?.close()
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
  current.value = null
  events.value = []
  historyEvents.value = []
  prompt.value = ''
  if (route.name === 'Workspace' && route.query.task)
    router.replace({ name: 'Workspace' })
  nextTick(() => inputRef.value?.focus?.())
}

async function openTaskById(id: string) {
  await openTask(id)
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
    // 同步 URL，使侧栏「新工作任务」移除 query 时能正确回到空态，也便于刷新/分享
    router.replace({ name: 'Workspace', query: { task: task.taskId } })
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
    task_created: 'TASK', task_queued: 'QUEUE', message: 'INFO', plan_created: 'PLAN', step_planned: 'PLAN STEP',
    step_started: 'STEP', step_retry: 'RETRY', step_completed: 'STEP OK', step_failed: 'STEP ERROR',
    agent_handoff: 'HANDOFF', tool_started: 'TOOL START', tool_completed: 'TOOL OK', tool_failed: 'TOOL ERROR',
    metrics_updated: 'METRICS', artifact_created: 'ARTIFACT', review_started: 'REVIEW',
    review_completed: 'REVIEW OK', task_approval_required: 'APPROVAL', task_approval_granted: 'APPROVED',
    task_approval_rejected: 'REJECTED', task_paused: 'PAUSE', task_resumed: 'RESUME',
    task_cancelled: 'CANCEL', task_completed: 'DONE', task_failed: 'FAILED', task_dead_lettered: 'DLQ',
  }
  return map[type] || type
}

function handleDurationLabel(ms?: number) {
  if (!ms || ms <= 0)
    return ''
  const totalSeconds = Math.round(ms / 1000)
  if (totalSeconds < 60)
    return `${totalSeconds} 秒`
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes} 分 ${seconds} 秒`
}

function displayTokens(actual?: number, estimated?: number) {
  if (actual && actual > 0)
    return String(actual)
  return `~${estimated || 0}`
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

async function copyResult() {
  if (!current.value)
    return
  const text = current.value.result || current.value.planSummary || current.value.prompt || ''
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  }
  catch {
    message.error('复制失败')
  }
}

async function syncFromRoute() {
  const taskId = route.query.task
  if (typeof taskId === 'string' && taskId) {
    if (current.value?.taskId !== taskId)
      await openTaskById(taskId)
  }
  else {
    newTask()
  }
}

watch(
  () => route.query.task,
  (id, oldId) => {
    if (id === oldId)
      return
    if (typeof id === 'string' && id) {
      if (current.value?.taskId !== id)
        openTaskById(id)
    }
    else {
      newTask()
    }
  },
)

onMounted(async () => {
  window.addEventListener('keydown', handleGlobalKeydown)
  await Promise.all([loadTasks(), loadQuota()])
  await syncFromRoute()
  if (!isMobile.value)
    inputRef.value?.focus?.()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  if (refreshTimer)
    clearTimeout(refreshTimer)
  eventSource?.close()
})
</script>

<template>
  <div class="flex flex-col h-full min-w-0 bg-white text-neutral-900 dark:bg-[#101014] dark:text-neutral-100">
    <!-- 顶部栏 -->
    <header class="relative flex items-center justify-center flex-none h-14 px-3">
      <div class="absolute left-2 flex items-center gap-0.5">
        <button
          class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
          title="折叠侧边栏"
          @click="handleUpdateCollapsed"
        >
          <SvgIcon :icon="collapsed ? 'ri:menu-line' : 'ri:sidebar-fold-line'" class="text-lg" />
        </button>
        <button
          class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
          title="新工作任务"
          @click="newTask"
        >
          <SvgIcon icon="ri:edit-2-line" class="text-lg" />
        </button>
      </div>

      <div class="min-w-0 px-24 text-center">
        <div class="text-[15px] font-semibold truncate">{{ current?.title || current?.prompt || 'ChenManus 工作区' }}</div>
        <div class="mt-0.5 text-[11px] text-neutral-400">AI 生成可能有误 请核实</div>
      </div>

      <div class="absolute right-2 flex items-center gap-0.5">
        <button
          v-if="current"
          class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
          title="刷新任务"
          @click="refreshTask(current.taskId)"
        >
          <SvgIcon icon="ri:refresh-line" class="text-lg" />
        </button>
        <button
          v-if="current && isTaskRunning"
          class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
          title="暂停"
          @click="runAction('pause')"
        >
          <SvgIcon icon="ri:pause-line" class="text-lg" />
        </button>
        <button
          v-if="current && current.status === 'PAUSED'"
          class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
          title="继续"
          @click="runAction('resume')"
        >
          <SvgIcon icon="ri:play-line" class="text-lg" />
        </button>
        <button
          v-if="current && !['COMPLETED', 'FAILED', 'CANCELLED'].includes(current.status)"
          class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
          title="取消任务"
          @click="runAction('cancel')"
        >
          <SvgIcon icon="ri:stop-line" class="text-lg" />
        </button>
        <button
          v-if="current?.result"
          class="flex items-center justify-center w-9 h-9 rounded-lg text-neutral-500 hover:bg-black/5 dark:text-neutral-300 dark:hover:bg-white/5"
          title="复制结果"
          @click="copyResult"
        >
          <SvgIcon icon="ri:file-copy-line" class="text-lg" />
        </button>
      </div>
    </header>

    <!-- 消息区 -->
    <main class="flex-1 min-h-0 overflow-hidden">
      <div ref="scrollRef" class="h-full overflow-y-auto">
        <!-- 空态：欢迎页 -->
        <div v-if="!current" class="flex flex-col items-center min-h-full px-6 pt-16 pb-6 sm:pt-24">
          <div class="flex items-center justify-center w-14 h-14 mb-6 text-white bg-neutral-900 rounded-2xl dark:bg-white dark:text-neutral-900">
            <SvgIcon icon="ri:sparkling-2-line" class="text-3xl" />
          </div>
          <h1 class="text-3xl font-semibold tracking-tight sm:text-4xl">
            {{ currentHeadline }}
          </h1>
          <p class="max-w-2xl mt-4 text-sm leading-6 text-center text-neutral-500 dark:text-neutral-400">
            用一句自然语言描述你要完成的事情。ChenManus 会先规划，再调度多个 Agent 执行、校验和交付；涉及高风险动作时会停下来等你确认。
          </p>

          <div class="grid w-full max-w-3xl grid-cols-1 gap-3 mt-10 sm:grid-cols-2">
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
        </div>

        <!-- 任务对话流 -->
        <div v-else class="w-full max-w-[1000px] px-4 pt-6 pb-8 mx-auto sm:px-8">
          <!-- 用户消息（右侧气泡） -->
          <div class="flex justify-end">
            <div class="max-w-[85%] px-4 py-3 text-[15px] leading-7 whitespace-pre-wrap bg-[#f5f5f6] rounded-2xl dark:bg-[#26262d]">
              {{ current.prompt }}
            </div>
          </div>

          <!-- 处理时长 + 虚线分隔 -->
          <div class="flex items-center gap-3 my-5">
            <span class="flex items-center gap-1.5 flex-none text-[12px] text-neutral-400">
              <SvgIcon v-if="isTaskRunning" icon="ri:loader-4-line" class="text-sm animate-spin" />
              <SvgIcon v-else-if="current.status === 'COMPLETED'" icon="ri:check-line" class="text-sm text-emerald-500" />
              {{ isTaskRunning ? '处理中' : handleDurationLabel(current.durationMs) ? `已处理 ${handleDurationLabel(current.durationMs)}` : statusLabel(current.status) }}
            </span>
            <span class="flex-1 border-t border-dashed border-neutral-200 dark:border-neutral-800" />
          </div>

          <!-- 助手回复（左侧） -->
          <div class="flex gap-3">
            <div class="flex items-center justify-center flex-none w-8 h-8 mt-0.5 text-white bg-neutral-900 rounded-xl dark:bg-white dark:text-neutral-900">
              <SvgIcon icon="ri:sparkling-2-line" class="text-base" />
            </div>
            <div class="flex-1 min-w-0 space-y-4">
              <!-- 状态标题 -->
              <div class="flex flex-wrap items-center gap-2">
                <div class="text-[15px] font-semibold">{{ currentHeadline }}</div>
                <NTag size="small" :type="statusTagType(current.status)" round :bordered="false">
                  {{ statusLabel(current.status) }}
                </NTag>
                <span v-if="quota" class="text-[11px] text-neutral-400">
                  {{ quota.activeTasks }}/{{ quota.maxActiveTasksPerTenant || '∞' }} active
                </span>
              </div>

              <p v-if="current.planSummary" class="text-sm leading-6 text-neutral-600 dark:text-neutral-300">
                {{ current.planSummary }}
              </p>

              <!-- 待确认 -->
              <div v-if="pendingApproval" class="flex items-start gap-3 p-4 border rounded-2xl border-amber-200 bg-amber-50 dark:border-amber-500/20 dark:bg-amber-500/10">
                <span class="flex items-center justify-center flex-none w-9 h-9 bg-white rounded-xl text-amber-600 dark:bg-amber-500/10 dark:text-amber-400">
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

              <!-- 执行进度 -->
              <div v-if="steps.length" class="p-4 border rounded-2xl bg-neutral-50/70 border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                <div class="flex items-center justify-between gap-3">
                  <div class="text-sm font-medium">执行进度</div>
                  <div class="text-sm font-semibold tabular-nums text-neutral-500">{{ completedSteps }} / {{ steps.length }} · {{ stepPercent }}%</div>
                </div>
                <div class="w-full h-1.5 mt-3 overflow-hidden rounded-full bg-neutral-200/70 dark:bg-neutral-800">
                  <div class="h-full transition-all duration-500 rounded-full bg-neutral-900 dark:bg-white" :style="{ width: stepPercent + '%' }" />
                </div>
                <div class="mt-3 space-y-1">
                  <div
                    v-for="step in steps"
                    :key="step.stepId"
                    class="flex items-center gap-3 px-2.5 py-2 rounded-xl"
                    :class="step.approvalStatus === 'PENDING' ? 'bg-amber-50 dark:bg-amber-500/10' : 'bg-transparent'"
                  >
                    <span class="flex items-center justify-center flex-none w-6 h-6 rounded-lg bg-white text-neutral-500 border border-neutral-200/80 dark:bg-neutral-800 dark:border-neutral-700 dark:text-neutral-400">
                      <SvgIcon :icon="stepIcon(step)" :class="step.status === 'RUNNING' ? 'animate-spin' : ''" />
                    </span>
                    <span class="flex-1 min-w-0">
                      <span class="block text-sm truncate text-neutral-700 dark:text-neutral-200">{{ step.title }}</span>
                      <span class="block mt-0.5 text-[10px] truncate text-neutral-400">
                        {{ step.approvalStatus === 'PENDING' ? '等待人工确认' : step.status === 'RUNNING' ? '正在执行' : step.status === 'COMPLETED' ? '已完成' : step.status || '待执行' }}
                      </span>
                    </span>
                    <span v-if="step.parallelizable" class="text-[10px] text-neutral-400">并行</span>
                    <span v-if="step.retryCount" class="text-[10px] text-neutral-400">retry {{ step.retryCount }}</span>
                  </div>
                </div>
              </div>

              <!-- 指标 -->
              <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <div class="p-3.5 bg-neutral-50/70 border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                  <div class="text-[10px] text-neutral-400">耗时</div>
                  <div class="mt-1 text-sm font-semibold">{{ handleDurationLabel(current.durationMs) || '—' }}</div>
                </div>
                <div class="p-3.5 bg-neutral-50/70 border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                  <div class="text-[10px] text-neutral-400">输入</div>
                  <div class="mt-1 text-sm font-semibold">{{ displayTokens(current.actualInputTokens, current.estimatedInputTokens) }} <span class="text-[10px] font-normal text-neutral-400">tokens</span></div>
                </div>
                <div class="p-3.5 bg-neutral-50/70 border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                  <div class="text-[10px] text-neutral-400">输出</div>
                  <div class="mt-1 text-sm font-semibold">{{ displayTokens(current.actualOutputTokens, current.estimatedOutputTokens) }} <span class="text-[10px] font-normal text-neutral-400">tokens</span></div>
                </div>
                <div class="p-3.5 bg-neutral-50/70 border rounded-2xl border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                  <div class="text-[10px] text-neutral-400">模型调用</div>
                  <div class="mt-1 text-sm font-semibold">{{ current.modelCallCount || 0 }} <span class="text-[10px] font-normal text-neutral-400">calls</span></div>
                </div>
              </div>

              <!-- 交付产物（文件行样式） -->
              <section v-if="current.artifacts?.length" class="space-y-2">
                <div class="text-sm font-medium">交付产物</div>
                <div
                  v-for="artifact in current.artifacts"
                  :key="artifact.artifactId"
                  class="flex items-center gap-3 p-3 rounded-xl bg-neutral-50 dark:bg-neutral-900/60"
                >
                  <div class="flex items-center justify-center flex-none w-9 h-9 bg-white border border-neutral-200 rounded-lg text-neutral-500 dark:bg-neutral-800 dark:border-neutral-700">
                    <SvgIcon :icon="artifact.type === 'PDF' || artifact.mediaType === 'application/pdf' ? 'ri:file-pdf-2-line' : 'ri:file-3-line'" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <div class="text-sm truncate text-neutral-700 dark:text-neutral-200">{{ artifact.name }}</div>
                    <div class="mt-0.5 text-[10px] truncate text-neutral-400">{{ artifact.path }}</div>
                  </div>
                  <div class="flex items-center gap-3">
                    <a v-if="canPreview(artifact)" :href="artifactUrl(artifact, true)" target="_blank" rel="noreferrer" class="text-xs text-neutral-500 hover:text-neutral-900 dark:hover:text-white">预览</a>
                    <a :href="artifactUrl(artifact)" target="_blank" rel="noreferrer" class="text-xs text-neutral-500 hover:text-neutral-900 dark:hover:text-white">下载</a>
                  </div>
                </div>
              </section>

              <!-- Reviewer -->
              <section v-if="current.review" class="p-4 border rounded-2xl bg-neutral-50/70 border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                <div class="flex items-center justify-between gap-3">
                  <div class="text-sm font-medium">Reviewer 验收</div>
                  <NTag size="small" :type="current.review.passed ? 'success' : 'warning'" round :bordered="false">
                    {{ current.review.passed ? 'PASS' : 'NEEDS REPAIR' }}
                  </NTag>
                </div>
                <p class="mt-3 text-sm leading-6 text-neutral-600 dark:text-neutral-300">{{ current.review.feedback }}</p>
                <p v-if="current.review.missingItems" class="mt-2 text-xs text-amber-600 dark:text-amber-400">缺失项：{{ current.review.missingItems }}</p>
              </section>

              <!-- 最终结果 -->
              <section v-if="current.result" class="text-sm leading-7 whitespace-pre-wrap text-neutral-800 dark:text-neutral-100">
                {{ current.result }}
              </section>

              <!-- 活动日志 -->
              <section v-if="allEvents.length" class="p-4 border rounded-2xl bg-neutral-50/70 border-neutral-200/80 dark:bg-[#17171c] dark:border-neutral-800">
                <div class="flex items-center justify-between gap-3">
                  <div class="text-sm font-medium">活动日志</div>
                  <div class="flex items-center gap-2">
                    <span class="hidden text-[10px] text-neutral-400 sm:inline">{{ visibleEvents.length }} events</span>
                    <NSelect
                      v-model:value="eventFilter"
                      size="tiny"
                      class="!w-28 sm:!w-32"
                      :options="EVENT_FILTER_OPTIONS"
                    />
                  </div>
                </div>
                <div class="mt-3 space-y-2.5 max-h-72 overflow-y-auto">
                  <div v-for="(event, index) in visibleEvents.slice(0, 40)" :key="event.eventId || event.timestamp || index" class="flex gap-3">
                    <div class="flex flex-col items-center flex-none">
                      <span class="w-2 h-2 mt-1.5 rounded-full bg-neutral-300 dark:bg-neutral-700" />
                      <span v-if="index < visibleEvents.slice(0, 40).length - 1" class="w-px flex-1 mt-1 bg-neutral-200 dark:bg-neutral-800" />
                    </div>
                    <div class="flex-1 min-w-0 pb-1">
                      <div class="flex items-center gap-2">
                        <span class="text-[10px] font-medium tracking-wide text-neutral-400 uppercase">{{ eventLabel(event.displayType) }}</span>
                        <span class="text-[10px] text-neutral-300">{{ event.timestamp ? new Date(event.timestamp).toLocaleTimeString() : '' }}</span>
                      </div>
                      <p class="mt-0.5 text-xs leading-5 whitespace-pre-wrap text-neutral-500 dark:text-neutral-400">{{ event.message }}</p>
                    </div>
                  </div>
                </div>
              </section>

              <!-- 操作条 -->
              <div class="flex items-center gap-1 pt-1">
                <button
                  v-if="current.result"
                  class="flex items-center justify-center w-8 h-8 rounded-lg text-neutral-400 hover:bg-black/5 hover:text-neutral-600 dark:hover:bg-white/5"
                  title="复制结果"
                  @click="copyResult"
                >
                  <SvgIcon icon="ri:file-copy-line" class="text-base" />
                </button>
                <button
                  v-if="isTaskRunning"
                  class="flex items-center justify-center w-8 h-8 rounded-lg text-neutral-400 hover:bg-black/5 hover:text-neutral-600 dark:hover:bg-white/5"
                  title="暂停"
                  @click="runAction('pause')"
                >
                  <SvgIcon icon="ri:pause-line" class="text-base" />
                </button>
                <button
                  v-if="current.status === 'PAUSED'"
                  class="flex items-center justify-center w-8 h-8 rounded-lg text-neutral-400 hover:bg-black/5 hover:text-neutral-600 dark:hover:bg-white/5"
                  title="继续"
                  @click="runAction('resume')"
                >
                  <SvgIcon icon="ri:play-line" class="text-base" />
                </button>
                <button
                  v-if="!['COMPLETED', 'FAILED', 'CANCELLED'].includes(current.status)"
                  class="flex items-center justify-center w-8 h-8 rounded-lg text-neutral-400 hover:bg-black/5 hover:text-neutral-600 dark:hover:bg-white/5"
                  title="取消任务"
                  @click="runAction('cancel')"
                >
                  <SvgIcon icon="ri:stop-line" class="text-base" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 底部输入框 -->
    <footer class="flex-none px-3 pb-4 pt-2 sm:px-6">
      <div class="w-full max-w-[1000px] mx-auto">
        <div class="relative rounded-2xl border border-neutral-200 bg-white shadow-[0_2px_12px_rgba(0,0,0,0.06)] transition-shadow focus-within:shadow-[0_6px_24px_rgba(0,0,0,0.10)] dark:bg-[#2f2f2f] dark:border-neutral-700/80">
          <NInput
            ref="inputRef"
            v-model:value="prompt"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: isMobile ? 5 : 8 }"
            :disabled="loading"
            placeholder="发消息或创建任务…"
            :bordered="false"
            class="workspace-composer"
            @keypress="handleEnter"
          />
          <div class="flex items-center gap-2 px-3 pb-2.5 pt-1">
            <div class="flex-1" />

            <!-- 优先级 -->
            <NSelect v-model:value="priority" :options="priorityOptions" size="small" :bordered="false" class="priority-select !w-24" :disabled="loading" />

            <!-- 发送 / 停止 -->
            <button
              v-if="loading"
              type="button"
              class="flex items-center justify-center w-9 h-9 rounded-full bg-neutral-900 text-white hover:opacity-80 dark:bg-white dark:text-neutral-900"
              title="执行中"
            >
              <SvgIcon icon="ri:loader-4-line" class="text-base animate-spin" />
            </button>
            <button
              v-else
              type="button"
              class="flex items-center justify-center w-9 h-9 rounded-full transition-opacity disabled:cursor-not-allowed"
              :class="(loading || !prompt.trim()) ? 'bg-neutral-200 text-neutral-400 dark:bg-neutral-700 dark:text-neutral-500' : 'bg-neutral-900 text-white hover:opacity-80 dark:bg-white dark:text-neutral-900'"
              :disabled="!prompt.trim()"
              title="发送"
              @click="createTask"
            >
              <SvgIcon icon="ri:arrow-up-line" class="text-lg" />
            </button>
          </div>
        </div>
        <div class="pt-2 text-center text-[11px] text-neutral-400 dark:text-neutral-500">
          ChenManus 会自主规划与执行，涉及高风险操作时会请求你的确认。
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
:deep(.workspace-composer .n-input-wrapper) {
  padding: 12px 16px 8px;
}

:deep(.workspace-composer .n-input__textarea-el) {
  font-size: 15px;
  line-height: 1.55;
}

:deep(.priority-select .n-base-selection) {
  background: #f4f4f5;
  border-radius: 9999px;
  padding: 0 6px;
}

:deep(.priority-select .n-base-selection-label) {
  background: transparent;
  border-radius: 9999px;
}

:deep(.priority-select .n-base-selection__border),
:deep(.priority-select .n-base-selection__state-border) {
  display: none;
}

:deep(.priority-select .n-base-selection:hover) {
  background: #e9e9eb;
}

:deep(.workspace-composer .n-input__textarea-el::placeholder) {
  color: #a3a3a3;
}
</style>

<style>
.dark .priority-select .n-base-selection {
  background: #3a3a3d;
}

.dark .priority-select .n-base-selection:hover {
  background: #48484c;
}

.dark .workspace-composer .n-input__textarea-el::placeholder {
  color: #68686f;
}
</style>
