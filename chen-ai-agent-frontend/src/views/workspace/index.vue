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
  { label: '低', value: 'LOW' },
  { label: '普通', value: 'NORMAL' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'CRITICAL' },
]

const suggestions = [
  { label: '研究主题', prompt: '研究 AI Agent 最近的发展并整理成报告' },
  { label: '分析代码', prompt: '分析这个 Java 项目的代码问题' },
  { label: '生产发布', prompt: '部署这个项目到生产环境，发布前需要人工审批' },
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

const containerClass = computed(() => [
  'h-full',
  { 'pl-[260px]': !isMobile.value && !collapsed.value },
])

const panelClass = computed(() => (isMobile.value
  ? ['rounded-none', 'shadow-none']
  : ['border', 'rounded-md', 'shadow-md', 'dark:border-neutral-800']))

const siderMobileClass = computed<CSSProperties>(() => {
  if (isMobile.value)
    return { position: 'fixed', zIndex: 50 }
  return {}
})

const mobileSafeArea = computed(() => (isMobile.value
  ? { paddingBottom: 'env(safe-area-inset-bottom)' }
  : {}))

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
  catch (error) {
    // legacy 安全模式下配额接口可能 403，静默处理
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
    // SSE 断开时浏览器会自动重连，这里忽略
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
    await TaskApi.approve(scope, current.value.taskId, '工作区人工批准')
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
    CREATED: '已创建', QUEUED: '排队中', PLANNING: '规划中', RUNNING: '执行中',
    PAUSED: '已暂停', WAITING_USER: '等待人工审批', REVIEWING: '审核中', COMPLETED: '已完成',
    FAILED: '失败', CANCELLED: '已取消',
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
    case 'CANCELLED':
    case 'PAUSED':
      return 'default'
    default:
      return 'default'
  }
}

function dotClass(status?: string) {
  switch (status) {
    case 'RUNNING':
    case 'PLANNING':
    case 'QUEUED':
      return 'bg-[#4b9e5f]'
    case 'WAITING_USER':
      return 'bg-amber-500'
    case 'COMPLETED':
      return 'bg-neutral-400'
    case 'FAILED':
      return 'bg-red-500'
    case 'CANCELLED':
      return 'bg-neutral-300'
    default:
      return 'bg-neutral-300'
  }
}

function stepIcon(step: any) {
  if (step.status === 'COMPLETED')
    return 'ri:check-line'
  if (step.status === 'FAILED')
    return 'ri:close-line'
  if (step.approvalStatus === 'PENDING')
    return 'ri:flag-line'
  if (step.status === 'RUNNING')
    return 'ri:loader-4-line'
  return step.parallelizable ? 'ri:split-cells-horizontal' : 'ri:checkbox-blank-circle-line'
}

function eventLabel(type: string) {
  const map: Record<string, string> = {
    task_created: 'TASK', task_queued: 'QUEUE', plan_created: 'PLAN', step_planned: 'PLAN STEP',
    step_started: 'STEP', step_retry: 'RETRY', step_completed: 'STEP OK', step_failed: 'STEP ERROR',
    agent_handoff: 'A2A HANDOFF', tool_started: 'TOOL START', tool_completed: 'TOOL OK', tool_failed: 'TOOL ERROR',
    metrics_updated: 'METRICS', artifact_created: 'ARTIFACT', review_started: 'REVIEW',
    review_completed: 'REVIEW OK', task_approval_required: 'APPROVAL?', task_approval_granted: 'APPROVED',
    task_approval_rejected: 'REJECTED', task_paused: 'PAUSE', task_resumed: 'RESUME',
    task_cancelled: 'CANCEL', task_completed: 'DONE', task_failed: 'FAILED', task_dead_lettered: 'DLQ',
  }
  return map[type] || type
}

function formatDuration(ms?: number) {
  if (!ms)
    return '—'
  if (ms < 1000)
    return `${ms} ms`
  const seconds = Math.round(ms / 100) / 10
  if (seconds < 60)
    return `${seconds} s`
  return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`
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
  <div class="h-full dark:bg-[#24272e] transition-all" :class="isMobile ? 'p-0' : 'p-4'">
    <div class="h-full overflow-hidden bg-white dark:bg-[#101014]" :class="panelClass">
      <NLayout class="z-40 transition" :class="containerClass" has-sider>
        <NLayoutSider
          :collapsed="collapsed"
          :collapsed-width="0"
          :width="260"
          :show-trigger="isMobile ? false : 'arrow-circle'"
          collapse-mode="transform"
          position="absolute"
          bordered
          :style="siderMobileClass"
          @update-collapsed="handleUpdateCollapsed"
        >
          <div class="flex flex-col h-full bg-white dark:bg-[#101014]" :style="mobileSafeArea">
            <main class="flex flex-col flex-1 min-h-0">
              <div class="flex flex-col gap-2 p-4">
                <button
                  class="relative flex items-center gap-3 px-3 py-3 break-all border rounded-md cursor-pointer bg-neutral-100 border-[#4b9e5f] text-[#4b9e5f] dark:bg-[#24272e] dark:border-[#4b9e5f]"
                >
                  <SvgIcon icon="ri:sparkling-2-line" />
                  <span class="flex-1 text-sm font-medium text-left">ChenManus 工作区</span>
                </button>
                <button
                  class="relative flex items-center gap-3 px-3 py-3 break-all border rounded-md cursor-pointer hover:bg-neutral-100 dark:border-neutral-800 dark:hover:bg-[#24272e] text-neutral-600 dark:text-neutral-300"
                  @click="openChatApp('assistant')"
                >
                  <SvgIcon icon="ri:robot-2-line" />
                  <span class="flex-1 text-sm text-left">智能助手</span>
                </button>
                <button
                  class="relative flex items-center gap-3 px-3 py-3 break-all border rounded-md cursor-pointer hover:bg-neutral-100 dark:border-neutral-800 dark:hover:bg-[#24272e] text-neutral-600 dark:text-neutral-300"
                  @click="openChatApp('love')"
                >
                  <SvgIcon icon="ri:heart-3-line" />
                  <span class="flex-1 text-sm text-left">AI 恋爱大师</span>
                </button>
                <NButton dashed block class="mt-1" @click="newTask">
                  <template #icon>
                    <SvgIcon icon="ri:add-line" />
                  </template>
                  新建任务
                </NButton>
              </div>
              <div class="flex-1 min-h-0 pb-2 overflow-hidden">
                <NScrollbar class="px-4">
                  <div class="flex flex-col gap-2 text-sm">
                    <div v-if="!tasks.length" class="flex flex-col items-center mt-4 text-center text-neutral-300 dark:text-neutral-600">
                      <SvgIcon icon="ri:inbox-line" class="mb-2 text-3xl" />
                      <span>暂无任务</span>
                    </div>
                    <button
                      v-for="task in tasks"
                      :key="task.taskId"
                      class="relative flex items-center gap-3 px-3 py-3 break-all border rounded-md cursor-pointer hover:bg-neutral-100 dark:border-neutral-800 dark:hover:bg-[#24272e] text-neutral-600 dark:text-neutral-300"
                      :class="current?.taskId === task.taskId && ['border-[#4b9e5f]', 'bg-neutral-100', 'text-[#4b9e5f]', 'dark:bg-[#24272e]', 'dark:border-[#4b9e5f]']"
                      @click="openTask(task.taskId)"
                    >
                      <span :class="['inline-block w-2 h-2 rounded-full flex-none', dotClass(task.status)]" />
                      <span class="flex-1 overflow-hidden text-left break-all text-ellipsis whitespace-nowrap">
                        {{ task.title || task.prompt }}
                      </span>
                    </button>
                  </div>
                </NScrollbar>
              </div>
              <div class="px-4 pb-2 text-[11px] text-neutral-400 dark:text-neutral-600 truncate">
                租户 {{ scope.tenantId }} · {{ scope.sessionId.slice(0, 8) }}…
              </div>
            </main>
            <SiderFooter />
          </div>
        </NLayoutSider>

        <template v-if="isMobile">
          <div v-show="!collapsed" class="fixed inset-0 z-40 w-full h-full bg-black/40" @click="handleUpdateCollapsed" />
        </template>

        <NLayoutContent class="h-full">
          <div class="flex flex-col w-full h-full">
            <header class="sticky top-0 left-0 right-0 z-30 border-b dark:border-neutral-800 bg-white/80 dark:bg-black/20 backdrop-blur">
              <div class="relative flex items-center justify-between min-w-0 h-14 overflow-hidden">
                <div class="flex items-center">
                  <button class="flex items-center justify-center w-11 h-11 text-neutral-600 dark:text-white" @click="handleUpdateCollapsed">
                    <SvgIcon v-if="collapsed" class="text-2xl" icon="ri:align-justify" />
                    <SvgIcon v-else class="text-2xl" icon="ri:align-right" />
                  </button>
                </div>
                <h1 class="flex-1 px-4 pr-6 overflow-hidden text-base font-medium select-none text-ellipsis whitespace-nowrap">
                  {{ current?.title || 'ChenManus 2.0 任务工作区' }}
                </h1>
                <div class="flex items-center gap-1 pr-2">
                  <NTag v-if="current" size="small" :type="statusTagType(current.status)" round>
                    {{ statusLabel(current.status) }}
                  </NTag>
                  <button v-if="current" class="flex items-center justify-center w-10 h-10 text-neutral-500 dark:text-white" title="新建任务" @click="newTask">
                    <SvgIcon class="text-xl" icon="ri:add-circle-line" />
                  </button>
                </div>
              </div>
            </header>

            <main class="flex-1 overflow-hidden">
              <div ref="scrollRef" class="h-full overflow-y-auto">
                <div class="w-full max-w-screen-xl m-auto p-4">
                  <!-- 空状态 -->
                  <div v-if="!current" class="flex flex-col items-center justify-center mt-16 text-center">
                    <div class="flex items-center justify-center w-16 h-16 mb-4 rounded-2xl bg-neutral-100 dark:bg-[#24272e] text-[#4b9e5f]">
                      <SvgIcon icon="ri:sparkling-2-line" class="text-4xl" />
                    </div>
                    <div class="text-xl font-medium text-neutral-700 dark:text-neutral-200">
                      把任务交给 ChenManus
                    </div>
                    <div class="mt-2 text-sm text-neutral-400 dark:text-neutral-500">
                      DAG 编排 · Team Handoff · 人工审批 · Reviewer 校验 · 产物交付
                    </div>
                    <div class="flex flex-wrap justify-center gap-2 mt-6">
                      <button
                        v-for="s in suggestions"
                        :key="s.label"
                        class="px-4 py-2 text-sm border rounded-full text-neutral-500 hover:bg-neutral-100 dark:border-neutral-800 dark:text-neutral-400 dark:hover:bg-[#24272e]"
                        @click="prompt = s.prompt; inputRef?.focus?.()"
                      >
                        {{ s.label }}
                      </button>
                    </div>
                  </div>

                  <!-- 任务详情 -->
                  <div v-else class="flex flex-col gap-3 pb-4">
                    <div class="flex flex-wrap items-center gap-2">
                      <NTag size="small" :type="statusTagType(current.status)" round>
                        {{ statusLabel(current.status) }}
                      </NTag>
                      <span v-if="quota" class="text-xs text-neutral-400">
                        租户任务 {{ quota.activeTasks }}/{{ quota.maxActiveTasksPerTenant || '∞' }}
                      </span>
                      <span class="flex-1" />
                      <NButton v-if="pendingApproval" type="warning" size="small" @click="approveTask">
                        <template #icon><SvgIcon icon="ri:check-line" /></template>
                        批准执行
                      </NButton>
                      <NButton v-if="pendingApproval" size="small" @click="rejectTask">
                        驳回
                      </NButton>
                      <NButton v-if="['RUNNING', 'PLANNING', 'QUEUED'].includes(current.status || '')" size="small" secondary @click="runAction('pause')">
                        暂停
                      </NButton>
                      <NButton v-if="current.status === 'PAUSED'" size="small" secondary @click="runAction('resume')">
                        继续
                      </NButton>
                      <NButton v-if="!['COMPLETED', 'FAILED', 'CANCELLED'].includes(current.status || '')" size="small" quaternary @click="runAction('cancel')">
                        取消
                      </NButton>
                    </div>

                    <div v-if="pendingApproval" class="flex items-start gap-3 p-4 border border-amber-200 rounded-md bg-amber-50 dark:bg-amber-500/10 dark:border-amber-500/30">
                      <SvgIcon icon="ri:flag-line" class="mt-0.5 text-xl text-amber-500" />
                      <div>
                        <div class="text-sm font-medium text-amber-700 dark:text-amber-400">需要人工审批</div>
                        <p class="mt-1 text-xs text-amber-600/80 dark:text-amber-300/70">
                          ChenManus 已暂停在有副作用的执行节点。批准后才会继续，不批准不会执行该动作。
                        </p>
                      </div>
                    </div>

                    <!-- 指标 -->
                    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
                      <div v-for="m in [
                        { label: '耗时', value: formatDuration(current.durationMs), unit: '' },
                        { label: '输入', value: displayTokens(current.actualInputTokens, current.estimatedInputTokens), unit: 'tokens' },
                        { label: '输出', value: displayTokens(current.actualOutputTokens, current.estimatedOutputTokens), unit: 'tokens' },
                        { label: '模型调用', value: current.modelCallCount || 0, unit: 'calls' },
                      ]" :key="m.label" class="p-4 border rounded-md dark:border-neutral-800">
                        <div class="text-xs text-neutral-400">{{ m.label }}</div>
                        <div class="mt-1 text-lg font-semibold text-neutral-700 dark:text-neutral-200">
                          {{ m.value }}
                          <span v-if="m.unit" class="ml-1 text-xs font-normal text-neutral-400">{{ m.unit }}</span>
                        </div>
                      </div>
                    </div>

                    <!-- 执行 DAG -->
                    <section class="p-4 border rounded-md dark:border-neutral-800">
                      <div class="flex items-center gap-2">
                        <h3 class="text-sm font-semibold text-neutral-700 dark:text-neutral-200">执行 DAG</h3>
                        <span class="text-xs text-neutral-400">{{ current.steps?.length || 0 }} steps</span>
                      </div>
                      <p v-if="current.planSummary" class="mt-2 text-xs leading-relaxed text-neutral-500 dark:text-neutral-400">{{ current.planSummary }}</p>
                      <div
                        v-for="step in current.steps"
                        :key="step.stepId"
                        class="flex gap-3 py-3 border-t first:border-t-0 dark:border-neutral-800"
                        :class="step.approvalStatus === 'PENDING' && 'px-2 -mx-2 rounded bg-amber-50 dark:bg-amber-500/10'"
                      >
                        <span
                          class="flex-none mt-0.5"
                          :class="[
                            step.status === 'COMPLETED' ? 'text-[#4b9e5f]' : '',
                            step.status === 'FAILED' ? 'text-red-500' : '',
                            step.status === 'RUNNING' ? 'text-[#4b9e5f] animate-spin' : '',
                            step.approvalStatus === 'PENDING' ? 'text-amber-500' : '',
                            !['COMPLETED', 'FAILED', 'RUNNING'].includes(step.status ?? '') && step.approvalStatus !== 'PENDING' ? 'text-neutral-300' : '',
                          ]"
                        >
                          <SvgIcon :icon="stepIcon(step)" />
                        </span>
                        <div class="flex-1 min-w-0">
                          <div class="flex flex-wrap items-center gap-2">
                            <b class="text-sm text-neutral-700 dark:text-neutral-200">{{ step.title }}</b>
                            <span v-if="step.dependsOn?.length" class="text-[11px] text-neutral-400">依赖 {{ step.dependsOn.join(', ') }}</span>
                            <span v-if="step.parallelizable" class="text-[11px] text-neutral-400">可并行</span>
                            <span v-if="step.approvalStatus === 'PENDING'" class="text-[11px] text-amber-500">待人工审批</span>
                            <span v-if="step.approvalStatus === 'APPROVED'" class="text-[11px] text-neutral-400">已审批</span>
                            <span v-if="step.approvalStatus === 'REJECTED'" class="text-[11px] text-red-400">已驳回</span>
                            <span v-if="step.retryCount" class="text-[11px] text-neutral-400">retry {{ step.retryCount }}</span>
                          </div>
                          <p class="mt-1 text-xs text-neutral-400">{{ step.description }}</p>
                          <p v-if="step.approvalNote" class="mt-1 text-[11px] text-neutral-400">审批备注：{{ step.approvalNote }}</p>
                          <p v-if="step.durationMs || step.actualInputTokens || step.estimatedInputTokens" class="mt-1 text-[11px] text-neutral-400">
                            {{ formatDuration(step.durationMs) }} ·
                            {{ displayTokens(step.actualInputTokens, step.estimatedInputTokens) }} in /
                            {{ displayTokens(step.actualOutputTokens, step.estimatedOutputTokens) }} out ·
                            {{ step.modelCallCount || 0 }} calls
                          </p>
                          <pre v-if="step.output" class="mt-2 p-2 overflow-auto text-xs whitespace-pre-wrap rounded bg-neutral-50 dark:bg-[#24272e] text-neutral-600 dark:text-neutral-300 max-h-56">{{ step.output }}</pre>
                          <pre v-else-if="step.error" class="mt-2 p-2 overflow-auto text-xs whitespace-pre-wrap rounded bg-red-50 dark:bg-red-500/10 text-red-600 max-h-56">{{ step.error }}</pre>
                        </div>
                      </div>
                    </section>

                    <!-- Reviewer -->
                    <section v-if="current.review" class="p-4 border rounded-md dark:border-neutral-800">
                      <div class="flex items-center gap-2">
                        <h3 class="text-sm font-semibold text-neutral-700 dark:text-neutral-200">Reviewer</h3>
                        <NTag size="tiny" :type="current.review.passed ? 'success' : 'warning'">
                          {{ current.review.passed ? 'PASS' : 'NEEDS REPAIR' }}
                        </NTag>
                      </div>
                      <p class="mt-2 text-xs leading-relaxed text-neutral-500 dark:text-neutral-400">{{ current.review.feedback }}</p>
                      <p v-if="current.review.missingItems" class="mt-1 text-xs text-amber-600">缺失项：{{ current.review.missingItems }}</p>
                    </section>

                    <!-- 交付产物 -->
                    <section v-if="current.artifacts?.length" class="p-4 border rounded-md dark:border-neutral-800">
                      <div class="flex items-center gap-2">
                        <h3 class="text-sm font-semibold text-neutral-700 dark:text-neutral-200">交付产物</h3>
                        <span class="text-xs text-neutral-400">{{ current.artifacts.length }}</span>
                      </div>
                      <div v-for="artifact in current.artifacts" :key="artifact.artifactId" class="flex items-center gap-3 py-3 border-t first:border-t-0 dark:border-neutral-800">
                        <span class="px-2 py-1 text-[11px] rounded bg-neutral-100 dark:bg-[#24272e] text-neutral-500 flex-none">
                          {{ artifact.type.toUpperCase() }} · v{{ artifact.version || 1 }}
                        </span>
                        <div class="flex-1 min-w-0">
                          <b class="text-sm text-neutral-700 dark:text-neutral-200">{{ artifact.name }}</b>
                          <p class="text-xs text-neutral-400 truncate">{{ artifact.path }}</p>
                        </div>
                        <div class="flex gap-2 flex-none">
                          <a v-if="canPreview(artifact)" :href="artifactUrl(artifact, true)" target="_blank" rel="noreferrer" class="text-xs text-neutral-500 hover:text-[#4b9e5f]">预览</a>
                          <a :href="artifactUrl(artifact)" target="_blank" rel="noreferrer" class="text-xs text-neutral-500 hover:text-[#4b9e5f]">下载</a>
                        </div>
                      </div>
                    </section>

                    <!-- 事件流 -->
                    <section v-if="events.length" class="p-4 border rounded-md dark:border-neutral-800">
                      <div class="flex items-center gap-2">
                        <h3 class="text-sm font-semibold text-neutral-700 dark:text-neutral-200">实时 / 审计事件</h3>
                        <span class="text-xs text-neutral-400">{{ events.length }}</span>
                      </div>
                      <div v-for="(event, index) in events" :key="`${event.eventId || event.timestamp}-${index}`" class="flex gap-3 py-2 border-t first:border-t-0 dark:border-neutral-800">
                        <span class="w-24 flex-none text-[11px] text-neutral-400 font-mono">{{ eventLabel(event.displayType) }}</span>
                        <p class="flex-1 m-0 text-xs text-neutral-500 dark:text-neutral-400 whitespace-pre-wrap">{{ event.message }}</p>
                      </div>
                    </section>

                    <!-- 最终结果 -->
                    <section v-if="current.result" class="p-4 border rounded-md dark:border-neutral-800">
                      <h3 class="text-sm font-semibold text-neutral-700 dark:text-neutral-200">最终结果</h3>
                      <pre class="mt-2 p-3 overflow-auto text-xs whitespace-pre-wrap rounded bg-neutral-50 dark:bg-[#24272e] text-neutral-600 dark:text-neutral-300 max-h-96">{{ current.result }}</pre>
                    </section>
                  </div>
                </div>
              </div>
            </main>

            <footer :class="isMobile ? 'sticky bottom-0 p-2 pr-3 bg-white dark:bg-[#101014]' : 'p-4'">
              <div class="w-full max-w-screen-xl m-auto">
                <div class="flex items-end justify-between gap-2">
                  <div class="flex-none w-24">
                    <NSelect
                      v-model:value="priority"
                      :options="priorityOptions"
                      :disabled="loading"
                      size="small"
                    />
                  </div>
                  <div class="flex-1 min-w-0">
                    <NInput
                      ref="inputRef"
                      v-model:value="prompt"
                      type="textarea"
                      :autosize="{ minRows: 1, maxRows: isMobile ? 4 : 8 }"
                      :disabled="loading"
                      placeholder="描述你想完成的任务……"
                      @keypress="handleEnter"
                    />
                  </div>
                  <NButton
                    type="primary"
                    class="flex-none"
                    :disabled="loading || !prompt.trim()"
                    :loading="loading"
                    @click="createTask"
                  >
                    <template #icon>
                      <span class="dark:text-black">
                        <SvgIcon icon="ri:send-plane-fill" />
                      </span>
                    </template>
                  </NButton>
                </div>
              </div>
            </footer>
          </div>
        </NLayoutContent>
      </NLayout>
    </div>
  </div>
</template>
