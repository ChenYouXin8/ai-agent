<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ENDPOINTS } from '../config/api.js'

const tasks = ref([])
const current = ref(null)
const prompt = ref('')
const priority = ref('NORMAL')
const quota = ref(null)
const loading = ref(false)
const events = ref([])
let eventSource

const tenantId = localStorage.getItem('chenmanus-tenant-id') || 'default'
const userId = localStorage.getItem('chenmanus-user-id') || crypto.randomUUID()
const sessionId = sessionStorage.getItem('chenmanus-session-id') || crypto.randomUUID()
localStorage.setItem('chenmanus-tenant-id', tenantId)
localStorage.setItem('chenmanus-user-id', userId)
sessionStorage.setItem('chenmanus-session-id', sessionId)

function scopeQuery() {
  const params = new URLSearchParams({ tenantId, userId })
  return `?${params.toString()}`
}

async function requestJson(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const body = await response.json()
  if (body.code !== 0) throw new Error(body.message || '请求失败')
  return body.data
}

function scopedTaskUrl() {
  const params = new URLSearchParams({ tenantId, userId, sessionId })
  return `${ENDPOINTS.tasks}?${params.toString()}`
}

async function loadQuota() {
  try { quota.value = await requestJson(ENDPOINTS.quota(`?${new URLSearchParams({ tenantId })}`)) }
  catch (error) { console.error('加载租户配额失败', error) }
}

async function loadTasks() {
  try { tasks.value = await requestJson(scopedTaskUrl()) }
  catch (error) { console.error('加载任务失败', error) }
}

async function refreshTask(id) {
  try { current.value = await requestJson(ENDPOINTS.task(id, scopeQuery())) }
  catch (error) { console.error('刷新任务失败', error) }
}

function pushEvent(event) {
  events.value.unshift(event)
  if (events.value.length > 120) events.value.pop()
}

function connectEvents(id) {
  eventSource?.close()
  events.value = []
  eventSource = new EventSource(ENDPOINTS.taskEvents(id, scopeQuery()))
  const eventNames = [
    'task_created', 'task_queued', 'plan_created', 'step_planned', 'step_started',
    'step_retry', 'step_completed', 'step_failed', 'agent_handoff',
    'tool_started', 'tool_completed', 'tool_failed', 'metrics_updated', 'artifact_created',
    'review_started', 'review_completed', 'task_paused', 'task_resumed', 'task_cancelled',
    'task_completed', 'task_failed', 'task_dead_lettered'
  ]
  eventNames.forEach((name) => {
    eventSource.addEventListener(name, async (message) => {
      try {
        const payload = JSON.parse(message.data)
        pushEvent({ ...payload, displayType: name })
        await refreshTask(id)
        await loadTasks()
        await loadQuota()
      } catch (error) { console.error('处理 Agent 事件失败', error) }
    })
  })
  eventSource.onerror = () => {}
}

async function openTask(id) {
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
  if (!value || loading.value) return
  loading.value = true
  try {
    const task = await requestJson(ENDPOINTS.tasks, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: value, tenantId, userId, sessionId, priority: priority.value }),
    })
    prompt.value = ''
    await Promise.all([loadTasks(), loadQuota()])
    await openTask(task.taskId)
  } catch (error) {
    console.error('创建任务失败', error)
    window.alert(error.message)
  } finally { loading.value = false }
}

async function action(name) {
  if (!current.value) return
  try {
    await requestJson(ENDPOINTS[name](current.value.taskId, scopeQuery()), { method: 'POST' })
    await Promise.all([refreshTask(current.value.taskId), loadTasks(), loadQuota()])
  } catch (error) { window.alert(error.message) }
}

function statusLabel(status) {
  return {
    CREATED: '已创建', QUEUED: '排队中', PLANNING: '规划中', RUNNING: '执行中',
    PAUSED: '已暂停', REVIEWING: '审核中', COMPLETED: '已完成', FAILED: '失败',
    CANCELLED: '已取消',
  }[status] || status
}

function stepIcon(step) {
  if (step.status === 'COMPLETED') return '✓'
  if (step.status === 'FAILED') return '!'
  if (step.status === 'RUNNING') return '●'
  return step.parallelizable ? 'Ⅱ' : '○'
}

function eventLabel(type) {
  return {
    task_created: 'TASK', task_queued: 'QUEUE', plan_created: 'PLAN', step_planned: 'PLAN STEP',
    step_started: 'STEP', step_retry: 'RETRY', step_completed: 'STEP OK', step_failed: 'STEP ERROR',
    agent_handoff: 'A2A HANDOFF', tool_started: 'TOOL START', tool_completed: 'TOOL OK', tool_failed: 'TOOL ERROR',
    metrics_updated: 'METRICS', artifact_created: 'ARTIFACT', review_started: 'REVIEW',
    review_completed: 'REVIEW OK', task_paused: 'PAUSE', task_resumed: 'RESUME',
    task_cancelled: 'CANCEL', task_completed: 'DONE', task_failed: 'FAILED', task_dead_lettered: 'DLQ',
  }[type] || type
}

function formatDuration(ms) {
  if (!ms) return '—'
  if (ms < 1000) return `${ms} ms`
  const seconds = Math.round(ms / 100) / 10
  if (seconds < 60) return `${seconds} s`
  return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`
}

function displayTokens(actual, estimated) {
  if (actual && actual > 0) return String(actual)
  return `~${estimated || 0}`
}

function canPreview(artifact) {
  return artifact?.mediaType === 'application/pdf'
    || artifact?.mediaType?.startsWith('image/')
    || artifact?.mediaType?.startsWith('text/')
}

function artifactUrl(artifact, preview = false) {
  const query = scopeQuery()
  return preview
    ? ENDPOINTS.artifactPreview(current.value.taskId, artifact.artifactId, query)
    : ENDPOINTS.artifact(current.value.taskId, artifact.artifactId, query)
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
      <div class="logo"><b>✦</b><span>ChenManus <i>2.8</i></span></div>
      <button class="new" @click="newTask">＋ 新建任务</button>
      <small class="label">租户 / 会话</small>
      <div class="scope"><span>{{ tenantId }}</span><span>{{ sessionId.slice(0, 8) }}…</span></div>
      <small class="label">最近任务</small>
      <button v-for="task in tasks" :key="task.taskId" class="history" :class="{ active: current?.taskId === task.taskId }" @click="openTask(task.taskId)">
        <em :class="task.status?.toLowerCase()"></em><span>{{ task.title || task.prompt }}</span>
      </button>
    </aside>

    <main class="main">
      <header>
        <div><small>AGENT WORKSPACE · TENANT {{ tenantId }}</small><h1>{{ current?.title || 'ChenManus 2.8' }}</h1></div>
        <router-link to="/">返回</router-link>
      </header>

      <section v-if="!current" class="empty">
        <div>✦</div><h2>把任务交给 ChenManus</h2><p>DAG · Team Handoff · Redis Queue · Reviewer · Usage · Artifact</p>
        <div class="chips">
          <button @click="prompt='研究 AI Agent 最近的发展并整理成报告'">研究主题</button>
          <button @click="prompt='分析这个 Java 项目的代码问题'">分析代码</button>
          <button @click="prompt='整理一份 PDF 报告'">生成文档</button>
        </div>
      </section>

      <section v-else class="content">
        <div class="bar">
          <span>{{ statusLabel(current.status) }}</span>
          <span v-if="quota" class="quota">租户任务 {{ quota.activeTasks }}/{{ quota.maxActiveTasksPerTenant || '∞' }}</span>
          <button v-if="current.status === 'RUNNING' || current.status === 'PLANNING' || current.status === 'QUEUED'" @click="action('taskPause')">暂停</button>
          <button v-if="current.status === 'PAUSED'" @click="action('taskResume')">继续</button>
          <button v-if="!['COMPLETED','FAILED','CANCELLED'].includes(current.status)" @click="action('taskCancel')">取消</button>
        </div>

        <article class="metrics">
          <div><small>耗时</small><b>{{ formatDuration(current.durationMs) }}</b></div>
          <div><small>输入</small><b>{{ displayTokens(current.actualInputTokens, current.estimatedInputTokens) }}</b><span>tokens</span></div>
          <div><small>输出</small><b>{{ displayTokens(current.actualOutputTokens, current.estimatedOutputTokens) }}</b><span>tokens</span></div>
          <div><small>模型调用</small><b>{{ current.modelCallCount || 0 }}</b><span>calls</span></div>
        </article>

        <article>
          <div class="section-title"><h3>执行 DAG</h3><small>{{ current.steps?.length || 0 }} steps</small></div>
          <p v-if="current.planSummary" class="summary">{{ current.planSummary }}</p>
          <div v-for="step in current.steps" :key="step.stepId" class="step">
            <strong :class="step.status?.toLowerCase()">{{ stepIcon(step) }}</strong>
            <div class="step-body">
              <div class="step-head">
                <b>{{ step.title }}</b>
                <span v-if="step.dependsOn?.length">依赖 {{ step.dependsOn.join(', ') }}</span>
                <span v-if="step.parallelizable">可并行</span>
                <span v-if="step.retryCount">retry {{ step.retryCount }}</span>
              </div>
              <p>{{ step.description }}</p>
              <p v-if="step.durationMs || step.actualInputTokens || step.estimatedInputTokens" class="step-meta">
                {{ formatDuration(step.durationMs) }} · {{ displayTokens(step.actualInputTokens, step.estimatedInputTokens) }} in / {{ displayTokens(step.actualOutputTokens, step.estimatedOutputTokens) }} out · {{ step.modelCallCount || 0 }} calls
              </p>
              <pre v-if="step.output">{{ step.output }}</pre>
              <pre v-else-if="step.error" class="error">{{ step.error }}</pre>
            </div>
          </div>
        </article>

        <article v-if="current.review">
          <div class="section-title"><h3>Reviewer</h3><small>{{ current.review.passed ? 'PASS' : 'NEEDS REPAIR' }}</small></div>
          <p class="summary">{{ current.review.feedback }}</p>
          <p v-if="current.review.missingItems" class="review-missing">缺失项：{{ current.review.missingItems }}</p>
        </article>

        <article v-if="current.artifacts?.length">
          <div class="section-title"><h3>交付产物</h3><small>{{ current.artifacts.length }}</small></div>
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
          <div class="section-title"><h3>实时事件</h3><small>{{ events.length }}</small></div>
          <div v-for="(event, index) in events" :key="`${event.timestamp}-${index}`" class="event"><span>{{ eventLabel(event.displayType) }}</span><p>{{ event.message }}</p></div>
        </article>

        <article v-if="current.result"><div class="section-title"><h3>最终结果</h3></div><pre>{{ current.result }}</pre></article>
      </section>

      <form class="composer" @submit.prevent="createTask">
        <textarea v-model="prompt" rows="2" :disabled="loading" placeholder="描述你想完成的任务……"></textarea>
        <select v-model="priority" :disabled="loading" aria-label="任务优先级">
          <option value="LOW">低</option>
          <option value="NORMAL">普通</option>
          <option value="HIGH">高</option>
          <option value="CRITICAL">紧急</option>
        </select>
        <button :disabled="loading || !prompt.trim()">{{ loading ? '创建中…' : '开始任务 ↑' }}</button>
      </form>
    </main>
  </div>
</template>

<style scoped>
.cm{height:100%;display:flex;background:#f7f7f8;color:#18181b}.side{width:245px;background:#fff;border-right:1px solid #e5e5e7;padding:20px 13px;overflow:auto}.logo{display:flex;align-items:center;gap:9px;padding:0 7px 20px;font-size:17px}.logo b{display:grid;place-items:center;width:31px;height:31px;border-radius:9px;background:#18181b;color:#fff}.logo i{font-style:normal;font-size:10px;color:#999}.new{width:100%;border:0;border-radius:9px;padding:11px;text-align:left;background:#18181b;color:#fff}.label{display:block;color:#999;font-size:10px;text-transform:uppercase;margin:20px 7px 6px}.scope{display:flex;flex-direction:column;gap:3px;padding:8px 9px;background:#f7f7f8;border:1px solid #ececef;border-radius:8px;font:10px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace;color:#666}.history{display:flex;align-items:center;gap:8px;width:100%;border:0;background:transparent;text-align:left;padding:10px 7px;border-radius:8px;color:#555}.history span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history.active,.history:hover{background:#f1f1f3}.history em{width:7px;height:7px;border-radius:50%;background:#ccc;flex:none}.history em.running,.history em.planning,.history em.queued{background:#18181b}.history em.completed{background:#4b5563}.main{flex:1;min-width:0;display:flex;flex-direction:column}header{height:68px;background:#fff;border-bottom:1px solid #e5e5e7;padding:0 30px;display:flex;align-items:center;justify-content:space-between}header small{font-size:9px;letter-spacing:1.5px;color:#999}header h1{font-size:16px;margin:2px 0}header a{font-size:12px;color:#777;text-decoration:none}.empty{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center}.empty>div{font-size:42px}.empty h2{font-size:28px;margin:12px 0 7px}.empty p{color:#888}.chips{display:flex;gap:8px;margin-top:24px}.chips button{border:1px solid #ddd;background:#fff;border-radius:20px;padding:8px 13px;color:#555}.content{flex:1;overflow:auto;padding:26px max(22px,7vw) 125px}.bar{display:flex;gap:7px;align-items:center;margin-bottom:13px;flex-wrap:wrap}.bar span{font-size:10px;background:#e9e9ec;border-radius:20px;padding:5px 8px}.bar .quota{background:#fff;border:1px solid #e3e3e7}.bar button{background:#fff;border:1px solid #ddd;border-radius:7px;padding:5px 9px;font-size:12px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;padding:0;overflow:hidden}.metrics>div{padding:13px 15px;border-right:1px solid #ececf0}.metrics>div:last-child{border-right:0}.metrics small{display:block;color:#999;font-size:10px;margin-bottom:3px}.metrics b{font-size:16px}.metrics span{font-size:9px;color:#aaa;margin-left:3px}article{background:#fff;border:1px solid #e5e5e8;border-radius:13px;padding:17px;margin-bottom:13px}.section-title{display:flex;align-items:center;gap:8px}.section-title h3{font-size:14px;margin:0}.section-title small{font-weight:400;color:#999;font-size:10px}.summary{font-size:12px;line-height:1.6;color:#777;margin:8px 0 0}.review-missing{font-size:11px;color:#9a3412;white-space:pre-wrap}.step{display:flex;gap:12px;border-top:1px solid #f0f0f2;padding:12px 0}.step>strong{width:18px;text-align:center;color:#999}.step>strong.running{color:#18181b}.step>strong.failed{color:#b91c1c}.step-body{flex:1;min-width:0}.step-head{display:flex;justify-content:flex-start;align-items:center;flex-wrap:wrap;gap:8px}.step-head b{font-size:13px}.step-head span{font-size:9px;color:#999}.step p{font-size:11px;color:#999;margin:3px 0;white-space:pre-wrap}.step-meta{font-size:10px!important;color:#aaa!important}.step pre,article>pre{white-space:pre-wrap;max-height:220px;overflow:auto;font:12px/1.6 inherit;color:#555}.error{color:#b91c1c!important}.artifact{display:flex;gap:10px;align-items:center;border-top:1px solid #f0f0f2;padding:10px 0}.artifact>span{font-size:9px;background:#f1f1f3;border-radius:5px;padding:4px 6px;color:#666;flex:none}.artifact-body{flex:1;min-width:0}.artifact b{font-size:12px}.artifact p{font-size:10px;color:#999;margin:2px 0;word-break:break-all}.artifact small{font:9px ui-monospace,SFMono-Regular,Menlo,monospace;color:#aaa}.artifact-actions{display:flex;gap:8px;flex:none}.artifact-actions a{font-size:10px;color:#555;text-decoration:none;border:1px solid #ddd;border-radius:6px;padding:5px 7px}.event{display:flex;gap:12px;border-top:1px solid #f0f0f2;padding:8px 0;font-size:10px}.event span{width:90px;color:#999;flex:none}.event p{margin:0;color:#555;white-space:pre-wrap}.composer{position:fixed;bottom:18px;left:calc(245px + 50%);transform:translateX(-50%);width:min(780px,calc(100% - 295px));display:flex;gap:7px;background:#fff;border:1px solid #ddd;border-radius:15px;padding:7px;box-shadow:0 8px 28px rgba(0,0,0,.08)}.composer textarea{flex:1;min-width:0;border:0;outline:0;resize:none;padding:9px;font:13px/1.5 inherit}.composer select{align-self:flex-end;border:1px solid #ddd;border-radius:8px;padding:8px 8px;background:#fff;color:#555}.composer button{align-self:flex-end;border:0;border-radius:9px;padding:9px 13px;background:#18181b;color:#fff}.composer button:disabled{opacity:.4}@media(max-width:760px){.side{width:190px}.metrics{grid-template-columns:repeat(2,1fr)}.composer{left:calc(190px + 50%);width:calc(100% - 230px)}.chips{flex-wrap:wrap}}
</style>
