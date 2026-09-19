<script setup lang='ts'>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
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
import { useChatStore } from '@/store'

const message = useMessage()
const chatStore = useChatStore()

const tasks = ref<ChenTask[]>([])
const current = ref<ChenTask | null>(null)
const prompt = ref('')
const priority = ref<TaskPriority>('NORMAL')
const quota = ref<QuotaView | null>(null)
const loading = ref(false)
const events = ref<(TaskEvent & { displayType: string })[]>([])

const scope: TaskScope = loadScope()
let eventSource: EventSource | null = null

const EVENT_NAMES = [
  'task_created', 'task_queued', 'plan_created', 'step_planned', 'step_started',
  'step_retry', 'step_completed', 'step_failed', 'agent_handoff',
  'tool_started', 'tool_completed', 'tool_failed', 'metrics_updated', 'artifact_created',
  'review_started', 'review_completed', 'task_approval_required', 'task_approval_granted',
  'task_approval_rejected', 'task_paused', 'task_resumed', 'task_cancelled',
  'task_completed', 'task_failed', 'task_dead_lettered',
]

function openChatApp(mode: 'assistant' | 'love') {
  chatStore.openApp(mode)
}

async function loadQuota() {
  try {
    quota.value = await TaskApi.quota(scope)
  }
  catch (error) {
    console.error('加载租户配额失败', error)
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
}

function newTask() {
  eventSource?.close()
  current.value = null
  events.value = []
  prompt.value = ''
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
  const note = window.prompt('请输入驳回原因（可选）', '') ?? ''
  if (!current.value)
    return
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

function stepIcon(step: any) {
  if (step.status === 'COMPLETED')
    return '✓'
  if (step.status === 'FAILED')
    return '!'
  if (step.approvalStatus === 'PENDING')
    return '⚑'
  if (step.status === 'RUNNING')
    return '●'
  return step.parallelizable ? 'Ⅱ' : '○'
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

onMounted(() => {
  loadTasks()
  loadQuota()
})

onBeforeUnmount(() => eventSource?.close())
</script>

<template>
  <div class="cm">
    <aside class="side">
      <div class="logo">
        <b>✦</b><span>ChenManus <i>2.0</i></span>
      </div>

      <nav class="appnav">
        <button class="appnav-item active" title="ChenManus 2.0 任务工作区">
          <span class="appnav-icon">✦</span>
          <span class="appnav-text">ChenManus 工作区</span>
        </button>
        <button class="appnav-item" title="智能助手" @click="openChatApp('assistant')">
          <span class="appnav-icon">🤖</span>
          <span class="appnav-text">智能助手</span>
        </button>
        <button class="appnav-item" title="AI 恋爱大师" @click="openChatApp('love')">
          <span class="appnav-icon">💗</span>
          <span class="appnav-text">AI 恋爱大师</span>
        </button>
      </nav>

      <button class="new" @click="newTask">＋ 新建任务</button>
      <small class="label">租户 / 会话</small>
      <div class="scope">
        <span>{{ scope.tenantId }}</span>
        <span>{{ scope.sessionId.slice(0, 8) }}…</span>
      </div>
      <small class="label">最近任务</small>
      <button
        v-for="task in tasks"
        :key="task.taskId"
        class="history"
        :class="{ active: current?.taskId === task.taskId }"
        @click="openTask(task.taskId)"
      >
        <em :class="task.status?.toLowerCase()"></em>
        <span>{{ task.title || task.prompt }}</span>
      </button>
    </aside>

    <main class="main">
      <header>
        <div>
          <small>AGENT WORKSPACE · TENANT {{ scope.tenantId }}</small>
          <h1>{{ current?.title || 'ChenManus 2.0 任务工作区' }}</h1>
        </div>
      </header>

      <section v-if="!current" class="empty">
        <div>✦</div>
        <h2>把任务交给 ChenManus</h2>
        <p>DAG · Team Handoff · Redis Queue · Human Approval · Reviewer · Usage · Artifact</p>
        <div class="chips">
          <button @click="prompt = '研究 AI Agent 最近的发展并整理成报告'">研究主题</button>
          <button @click="prompt = '分析这个 Java 项目的代码问题'">分析代码</button>
          <button @click="prompt = '部署这个项目到生产环境，发布前需要人工审批'">生产发布</button>
        </div>
      </section>

      <section v-else class="content">
        <div class="bar">
          <span>{{ statusLabel(current.status) }}</span>
          <span v-if="quota" class="quota">
            租户任务 {{ quota.activeTasks }}/{{ quota.maxActiveTasksPerTenant || '∞' }}
          </span>
          <button v-if="current.status === 'WAITING_USER' && hasPendingApproval(current)" class="approve" @click="approveTask">
            批准执行
          </button>
          <button v-if="current.status === 'WAITING_USER' && hasPendingApproval(current)" class="reject" @click="rejectTask">
            驳回
          </button>
          <button v-if="['RUNNING', 'PLANNING', 'QUEUED'].includes(current.status || '')" @click="runAction('pause')">
            暂停
          </button>
          <button v-if="current.status === 'PAUSED'" @click="runAction('resume')">
            继续
          </button>
          <button v-if="!['COMPLETED', 'FAILED', 'CANCELLED'].includes(current.status || '')" @click="runAction('cancel')">
            取消
          </button>
        </div>

        <article v-if="current.status === 'WAITING_USER' && hasPendingApproval(current)" class="approval-banner">
          <div class="approval-icon">⚑</div>
          <div>
            <b>需要人工审批</b>
            <p>ChenManus 已暂停在有副作用的执行节点。批准后才会继续，不批准不会执行该动作。</p>
          </div>
        </article>

        <article class="metrics">
          <div>
            <small>耗时</small>
            <b>{{ formatDuration(current.durationMs) }}</b>
          </div>
          <div>
            <small>输入</small>
            <b>{{ displayTokens(current.actualInputTokens, current.estimatedInputTokens) }}</b><span>tokens</span>
          </div>
          <div>
            <small>输出</small>
            <b>{{ displayTokens(current.actualOutputTokens, current.estimatedOutputTokens) }}</b><span>tokens</span>
          </div>
          <div>
            <small>模型调用</small>
            <b>{{ current.modelCallCount || 0 }}</b><span>calls</span>
          </div>
        </article>

        <article>
          <div class="section-title">
            <h3>执行 DAG</h3>
            <small>{{ current.steps?.length || 0 }} steps</small>
          </div>
          <p v-if="current.planSummary" class="summary">{{ current.planSummary }}</p>
          <div
            v-for="step in current.steps"
            :key="step.stepId"
            class="step"
            :class="{ approval: step.approvalStatus === 'PENDING' }"
          >
            <strong :class="step.status?.toLowerCase()">{{ stepIcon(step) }}</strong>
            <div class="step-body">
              <div class="step-head">
                <b>{{ step.title }}</b>
                <span v-if="step.dependsOn?.length">依赖 {{ step.dependsOn.join(', ') }}</span>
                <span v-if="step.parallelizable">可并行</span>
                <span v-if="step.approvalStatus === 'PENDING'">待人工审批</span>
                <span v-if="step.approvalStatus === 'APPROVED'">已审批</span>
                <span v-if="step.approvalStatus === 'REJECTED'">已驳回</span>
                <span v-if="step.retryCount">retry {{ step.retryCount }}</span>
              </div>
              <p>{{ step.description }}</p>
              <p v-if="step.approvalNote" class="step-meta">审批备注：{{ step.approvalNote }}</p>
              <p v-if="step.durationMs || step.actualInputTokens || step.estimatedInputTokens" class="step-meta">
                {{ formatDuration(step.durationMs) }} ·
                {{ displayTokens(step.actualInputTokens, step.estimatedInputTokens) }} in /
                {{ displayTokens(step.actualOutputTokens, step.estimatedOutputTokens) }} out ·
                {{ step.modelCallCount || 0 }} calls
              </p>
              <pre v-if="step.output">{{ step.output }}</pre>
              <pre v-else-if="step.error" class="error">{{ step.error }}</pre>
            </div>
          </div>
        </article>

        <article v-if="current.review">
          <div class="section-title">
            <h3>Reviewer</h3>
            <small>{{ current.review.passed ? 'PASS' : 'NEEDS REPAIR' }}</small>
          </div>
          <p class="summary">{{ current.review.feedback }}</p>
          <p v-if="current.review.missingItems" class="review-missing">缺失项：{{ current.review.missingItems }}</p>
        </article>

        <article v-if="current.artifacts?.length">
          <div class="section-title">
            <h3>交付产物</h3>
            <small>{{ current.artifacts.length }}</small>
          </div>
          <div v-for="artifact in current.artifacts" :key="artifact.artifactId" class="artifact">
            <span>{{ artifact.type.toUpperCase() }} · v{{ artifact.version || 1 }}</span>
            <div class="artifact-body">
              <b>{{ artifact.name }}</b>
              <p>{{ artifact.path }}</p>
              <small v-if="artifact.checksum">SHA256 {{ artifact.checksum.slice(0, 16) }}…</small>
            </div>
            <div class="artifact-actions">
              <a v-if="canPreview(artifact)" :href="artifactUrl(artifact, true)" target="_blank" rel="noreferrer">预览</a>
              <a :href="artifactUrl(artifact)" target="_blank" rel="noreferrer">下载</a>
            </div>
          </div>
        </article>

        <article v-if="events.length">
          <div class="section-title">
            <h3>实时 / 审计事件</h3>
            <small>{{ events.length }}</small>
          </div>
          <div v-for="(event, index) in events" :key="`${event.eventId || event.timestamp}-${index}`" class="event">
            <span>{{ eventLabel(event.displayType) }}</span>
            <p>{{ event.message }}</p>
          </div>
        </article>

        <article v-if="current.result">
          <div class="section-title">
            <h3>最终结果</h3>
          </div>
          <pre>{{ current.result }}</pre>
        </article>
      </section>

      <form class="composer" @submit.prevent="createTask">
        <textarea
          v-model="prompt"
          rows="2"
          :disabled="loading"
          placeholder="描述你想完成的任务……"
        >
        </textarea>
        <select v-model="priority" :disabled="loading" aria-label="任务优先级">
          <option value="LOW">低</option>
          <option value="NORMAL">普通</option>
          <option value="HIGH">高</option>
          <option value="CRITICAL">紧急</option>
        </select>
        <button :disabled="loading || !prompt.trim()">
          {{ loading ? '创建中…' : '开始任务 ↑' }}
        </button>
      </form>
    </main>
  </div>
</template>

<style scoped>
.cm {
  height: 100%;
  display: flex;
  background: #f7f7f8;
  color: #18181b;
}
.side {
  width: 245px;
  background: #fff;
  border-right: 1px solid #e5e5e7;
  padding: 20px 13px;
  overflow: auto;
}
.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 7px 20px;
  font-size: 17px;
}
.logo b {
  display: grid;
  place-items: center;
  width: 31px;
  height: 31px;
  border-radius: 9px;
  background: #18181b;
  color: #fff;
}
.logo i {
  font-style: normal;
  font-size: 10px;
  color: #999;
}
.appnav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
}
.appnav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 10px 9px;
  border-radius: 9px;
  color: #555;
  font-size: 13px;
  cursor: pointer;
}
.appnav-item:hover {
  background: #f1f1f3;
}
.appnav-item.active {
  background: #18181b;
  color: #fff;
}
.appnav-icon {
  font-size: 15px;
  line-height: 1;
  width: 20px;
  text-align: center;
  flex: none;
}
.appnav-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.new {
  width: 100%;
  border: 0;
  border-radius: 9px;
  padding: 11px;
  text-align: left;
  background: #18181b;
  color: #fff;
  cursor: pointer;
}
.back {
  width: 100%;
  margin-top: 8px;
  border: 1px solid #ddd;
  border-radius: 9px;
  padding: 9px 11px;
  text-align: left;
  background: #fff;
  color: #555;
  cursor: pointer;
}
.back:hover {
  background: #f3f3f5;
}
.label {
  display: block;
  color: #999;
  font-size: 10px;
  text-transform: uppercase;
  margin: 20px 7px 6px;
}
.scope {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 9px;
  background: #f7f7f8;
  border: 1px solid #ececef;
  border-radius: 8px;
  font: 10px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #666;
}
.history {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 10px 7px;
  border-radius: 8px;
  color: #555;
  cursor: pointer;
}
.history span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history.active,
.history:hover {
  background: #f1f1f3;
}
.history em {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ccc;
  flex: none;
}
.history em.running,
.history em.planning,
.history em.queued {
  background: #18181b;
}
.history em.waiting_user {
  background: #d97706;
}
.history em.completed {
  background: #4b5563;
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
header {
  height: 68px;
  background: #fff;
  border-bottom: 1px solid #e5e5e7;
  padding: 0 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
header small {
  font-size: 9px;
  letter-spacing: 1.5px;
  color: #999;
}
header h1 {
  font-size: 16px;
  margin: 2px 0;
}
.header-back {
  font-size: 12px;
  color: #777;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 7px;
  padding: 6px 12px;
  cursor: pointer;
}
.header-back:hover {
  background: #f3f3f5;
}
.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.empty > div {
  font-size: 42px;
}
.empty h2 {
  font-size: 28px;
  margin: 12px 0 7px;
}
.empty p {
  color: #888;
}
.chips {
  display: flex;
  gap: 8px;
  margin-top: 24px;
}
.chips button {
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 20px;
  padding: 8px 13px;
  color: #555;
  cursor: pointer;
}
.content {
  flex: 1;
  overflow: auto;
  padding: 26px max(22px, 7vw) 125px;
}
.bar {
  display: flex;
  gap: 7px;
  align-items: center;
  margin-bottom: 13px;
  flex-wrap: wrap;
}
.bar span {
  font-size: 10px;
  background: #e9e9ec;
  border-radius: 20px;
  padding: 5px 8px;
}
.bar .quota {
  background: #fff;
  border: 1px solid #e3e3e7;
}
.bar button {
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 7px;
  padding: 5px 9px;
  font-size: 12px;
  cursor: pointer;
}
.bar button.approve {
  background: #18181b;
  color: #fff;
  border-color: #18181b;
}
.bar button.reject {
  color: #9a3412;
  border-color: #fed7aa;
}
.approval-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border-color: #f4d28b;
  background: #fffaf0;
}
.approval-icon {
  font-size: 24px;
  line-height: 1;
}
.approval-banner b {
  font-size: 13px;
}
.approval-banner p {
  font-size: 11px;
  color: #8a6d3b;
  margin: 3px 0 0;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  padding: 0;
  overflow: hidden;
}
.metrics > div {
  padding: 13px 15px;
  border-right: 1px solid #ececf0;
}
.metrics > div:last-child {
  border-right: 0;
}
.metrics small {
  display: block;
  color: #999;
  font-size: 10px;
  margin-bottom: 3px;
}
.metrics b {
  font-size: 16px;
}
.metrics span {
  font-size: 9px;
  color: #aaa;
  margin-left: 3px;
}
article {
  background: #fff;
  border: 1px solid #e5e5e8;
  border-radius: 13px;
  padding: 17px;
  margin-bottom: 13px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-title h3 {
  font-size: 14px;
  margin: 0;
}
.section-title small {
  font-weight: 400;
  color: #999;
  font-size: 10px;
}
.summary {
  font-size: 12px;
  line-height: 1.6;
  color: #777;
  margin: 8px 0 0;
}
.review-missing {
  font-size: 11px;
  color: #9a3412;
  white-space: pre-wrap;
}
.step {
  display: flex;
  gap: 12px;
  border-top: 1px solid #f0f0f2;
  padding: 12px 0;
}
.step.approval {
  background: #fffaf0;
  margin: 0 -6px;
  padding: 12px 6px;
  border-radius: 8px;
}
.step > strong {
  width: 18px;
  text-align: center;
  color: #999;
}
.step > strong.running {
  color: #18181b;
}
.step > strong.failed {
  color: #b91c1c;
}
.step-body {
  flex: 1;
  min-width: 0;
}
.step-head {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.step-head b {
  font-size: 13px;
}
.step-head span {
  font-size: 9px;
  color: #999;
}
.step p {
  font-size: 11px;
  color: #999;
  margin: 3px 0;
  white-space: pre-wrap;
}
.step-meta {
  font-size: 10px !important;
  color: #aaa !important;
}
.step pre,
article > pre {
  white-space: pre-wrap;
  max-height: 220px;
  overflow: auto;
  font: 12px/1.6 inherit;
  color: #555;
}
.error {
  color: #b91c1c !important;
}
.artifact {
  display: flex;
  gap: 10px;
  align-items: center;
  border-top: 1px solid #f0f0f2;
  padding: 10px 0;
}
.artifact > span {
  font-size: 9px;
  background: #f1f1f3;
  border-radius: 5px;
  padding: 4px 6px;
  color: #666;
  flex: none;
}
.artifact-body {
  flex: 1;
  min-width: 0;
}
.artifact b {
  font-size: 12px;
}
.artifact p {
  font-size: 10px;
  color: #999;
  margin: 2px 0;
  word-break: break-all;
}
.artifact small {
  font: 9px ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #aaa;
}
.artifact-actions {
  display: flex;
  gap: 8px;
  flex: none;
}
.artifact-actions a {
  font-size: 10px;
  color: #555;
  text-decoration: none;
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 5px 7px;
}
.event {
  display: flex;
  gap: 12px;
  border-top: 1px solid #f0f0f2;
  padding: 8px 0;
  font-size: 10px;
}
.event span {
  width: 90px;
  color: #999;
  flex: none;
}
.event p {
  margin: 0;
  color: #555;
  white-space: pre-wrap;
}
.composer {
  position: fixed;
  bottom: 18px;
  left: calc(245px + 50%);
  transform: translateX(-50%);
  width: min(780px, calc(100% - 295px));
  display: flex;
  gap: 7px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 15px;
  padding: 7px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
}
.composer textarea {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  resize: none;
  padding: 9px;
  font: 13px/1.5 inherit;
}
.composer select {
  align-self: flex-end;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 8px 8px;
  background: #fff;
  color: #555;
}
.composer button {
  align-self: flex-end;
  border: 0;
  border-radius: 9px;
  padding: 9px 13px;
  background: #18181b;
  color: #fff;
  cursor: pointer;
}
.composer button:disabled {
  opacity: 0.4;
}
@media (max-width: 760px) {
  .side {
    width: 190px;
  }
  .metrics {
    grid-template-columns: repeat(2, 1fr);
  }
  .composer {
    left: calc(190px + 50%);
    width: calc(100% - 230px);
  }
  .chips {
    flex-wrap: wrap;
  }
}
</style>
