<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ENDPOINTS } from '../config/api.js'

const tasks = ref([])
const current = ref(null)
const prompt = ref('')
const loading = ref(false)
const events = ref([])
let eventSource

async function requestJson(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const body = await response.json()
  if (body.code !== 0) throw new Error(body.message || '请求失败')
  return body.data
}

async function loadTasks() {
  try { tasks.value = await requestJson(ENDPOINTS.tasks) }
  catch (error) { console.error('加载任务失败', error) }
}

async function refreshTask(id) {
  try { current.value = await requestJson(ENDPOINTS.task(id)) }
  catch (error) { console.error('刷新任务失败', error) }
}

function pushEvent(event) {
  events.value.unshift(event)
  if (events.value.length > 80) events.value.pop()
}

function connectEvents(id) {
  eventSource?.close()
  events.value = []
  eventSource = new EventSource(ENDPOINTS.taskEvents(id))
  const eventNames = [
    'task_created', 'plan_created', 'step_planned', 'step_started',
    'step_retry', 'step_completed', 'step_failed', 'tool_started',
    'tool_completed', 'artifact_created', 'review_started', 'review_completed',
    'task_paused', 'task_resumed', 'task_cancelled', 'task_completed', 'task_failed'
  ]
  eventNames.forEach((name) => {
    eventSource.addEventListener(name, async (message) => {
      try {
        const payload = JSON.parse(message.data)
        pushEvent({ ...payload, displayType: name })
        await refreshTask(id)
        await loadTasks()
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
      body: JSON.stringify({ prompt: value }),
    })
    prompt.value = ''
    await loadTasks()
    await openTask(task.taskId)
  } catch (error) {
    console.error('创建任务失败', error)
    window.alert(error.message)
  } finally { loading.value = false }
}

async function action(name) {
  if (!current.value) return
  try {
    await requestJson(ENDPOINTS[name](current.value.taskId), { method: 'POST' })
    await refreshTask(current.value.taskId)
  } catch (error) { window.alert(error.message) }
}

function statusLabel(status) {
  return {
    CREATED: '已创建', PLANNING: '规划中', RUNNING: '执行中',
    PAUSED: '已暂停', REVIEWING: '审核中', COMPLETED: '已完成',
    FAILED: '失败', CANCELLED: '已取消',
  }[status] || status
}

function stepIcon(status) {
  return status === 'COMPLETED' ? '✓' : status === 'RUNNING' ? '●' : status === 'FAILED' ? '!' : '○'
}

function artifactType(path) {
  const match = path?.match(/\.([^.\\/]+)$/)
  return match ? match[1].toUpperCase() : 'FILE'
}

onMounted(loadTasks)
onBeforeUnmount(() => eventSource?.close())
</script>

<template>
  <div class="cm">
    <aside class="side">
      <div class="logo"><b>✦</b><span>ChenManus <i>2.0</i></span></div>
      <button class="new" @click="newTask">＋ 新建任务</button>
      <small class="label">最近任务</small>
      <button v-for="task in tasks" :key="task.taskId" class="history" :class="{ active: current?.taskId === task.taskId }" @click="openTask(task.taskId)">
        <em :class="task.status?.toLowerCase()"></em><span>{{ task.title || task.prompt }}</span>
      </button>
    </aside>

    <main class="main">
      <header>
        <div><small>AGENT WORKSPACE</small><h1>{{ current?.title || 'ChenManus 2.0' }}</h1></div>
        <router-link to="/">返回</router-link>
      </header>

      <section v-if="!current" class="empty">
        <div>✦</div><h2>把任务交给 ChenManus</h2><p>自主规划 · 逐步执行 · 自动重试 · 审核 · 产物</p>
        <div class="chips">
          <button @click="prompt='研究 AI Agent 最近的发展并整理成报告'">研究主题</button>
          <button @click="prompt='分析这个 Java 项目的代码问题'">分析代码</button>
          <button @click="prompt='整理一份 PDF 报告'">生成文档</button>
        </div>
      </section>

      <section v-else class="content">
        <div class="bar">
          <span>{{ statusLabel(current.status) }}</span>
          <button v-if="current.status === 'RUNNING'" @click="action('taskPause')">暂停</button>
          <button v-if="current.status === 'PAUSED'" @click="action('taskResume')">继续</button>
          <button v-if="!['COMPLETED','FAILED','CANCELLED'].includes(current.status)" @click="action('taskCancel')">取消</button>
        </div>

        <article>
          <div class="section-title"><h3>执行计划</h3><small>{{ current.steps?.length || 0 }} steps</small></div>
          <p v-if="current.planSummary" class="summary">{{ current.planSummary }}</p>
          <div v-for="step in current.steps" :key="step.stepId" class="step">
            <strong :class="step.status?.toLowerCase()">{{ stepIcon(step.status) }}</strong>
            <div class="step-body">
              <div class="step-head"><b>{{ step.title }}</b><span v-if="step.retryCount">retry {{ step.retryCount }}</span></div>
              <p>{{ step.description }}</p>
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
            <span>{{ artifactType(artifact.path) }}</span><div><b>{{ artifact.name }}</b><p>{{ artifact.path }}</p></div>
          </div>
        </article>

        <article v-if="events.length">
          <div class="section-title"><h3>实时事件</h3><small>{{ events.length }}</small></div>
          <div v-for="(event, index) in events" :key="`${event.timestamp}-${index}`" class="event"><span>{{ event.displayType }}</span><p>{{ event.message }}</p></div>
        </article>

        <article v-if="current.result"><div class="section-title"><h3>最终结果</h3></div><pre>{{ current.result }}</pre></article>
      </section>

      <form class="composer" @submit.prevent="createTask">
        <textarea v-model="prompt" rows="2" :disabled="loading" placeholder="描述你想完成的任务……"></textarea>
        <button :disabled="loading || !prompt.trim()">{{ loading ? '创建中…' : '开始任务 ↑' }}</button>
      </form>
    </main>
  </div>
</template>

<style scoped>
.cm{height:100%;display:flex;background:#f7f7f8;color:#18181b}.side{width:245px;background:#fff;border-right:1px solid #e5e5e7;padding:20px 13px;overflow:auto}.logo{display:flex;align-items:center;gap:9px;padding:0 7px 20px;font-size:17px}.logo b{display:grid;place-items:center;width:31px;height:31px;border-radius:9px;background:#18181b;color:#fff}.logo i{font-style:normal;font-size:10px;color:#999}.new{width:100%;border:0;border-radius:9px;padding:11px;text-align:left;background:#18181b;color:#fff}.label{display:block;color:#999;font-size:10px;text-transform:uppercase;margin:20px 7px 6px}.history{display:flex;align-items:center;gap:8px;width:100%;border:0;background:transparent;text-align:left;padding:10px 7px;border-radius:8px;color:#555}.history span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history.active,.history:hover{background:#f1f1f3}.history em{width:7px;height:7px;border-radius:50%;background:#ccc;flex:none}.history em.running,.history em.planning{background:#18181b}.history em.completed{background:#4b5563}.main{flex:1;min-width:0;display:flex;flex-direction:column}header{height:68px;background:#fff;border-bottom:1px solid #e5e5e7;padding:0 30px;display:flex;align-items:center;justify-content:space-between}header small{font-size:9px;letter-spacing:1.5px;color:#999}header h1{font-size:16px;margin:2px 0}header a{font-size:12px;color:#777;text-decoration:none}.empty{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center}.empty>div{font-size:42px}.empty h2{font-size:28px;margin:12px 0 7px}.empty p{color:#888}.chips{display:flex;gap:8px;margin-top:24px}.chips button{border:1px solid #ddd;background:#fff;border-radius:20px;padding:8px 13px;color:#555}.content{flex:1;overflow:auto;padding:26px max(22px,8vw) 125px}.bar{display:flex;gap:7px;align-items:center;margin-bottom:13px}.bar span{font-size:10px;background:#e9e9ec;border-radius:20px;padding:5px 8px}.bar button{background:#fff;border:1px solid #ddd;border-radius:7px;padding:5px 9px;font-size:12px}article{background:#fff;border:1px solid #e5e5e8;border-radius:13px;padding:17px;margin-bottom:13px}.section-title{display:flex;align-items:center;gap:8px}.section-title h3{font-size:14px;margin:0}.section-title small{font-weight:400;color:#999;font-size:10px}.summary{font-size:12px;line-height:1.6;color:#777;margin:8px 0 0}.review-missing{font-size:11px;color:#9a3412;white-space:pre-wrap}.step{display:flex;gap:12px;border-top:1px solid #f0f0f2;padding:12px 0}.step>strong{width:18px;text-align:center;color:#999}.step>strong.running{color:#18181b}.step>strong.failed{color:#b91c1c}.step-body{flex:1;min-width:0}.step-head{display:flex;justify-content:space-between;gap:12px}.step-head b{font-size:13px}.step-head span{font-size:9px;color:#999}.step p{font-size:11px;color:#999;margin:3px 0;white-space:pre-wrap}.step pre,article>pre{white-space:pre-wrap;max-height:220px;overflow:auto;font:12px/1.6 inherit;color:#555}.error{color:#b91c1c!important}.artifact{display:flex;gap:10px;align-items:center;border-top:1px solid #f0f0f2;padding:10px 0}.artifact>span{font-size:9px;background:#f1f1f3;border-radius:5px;padding:4px 6px;color:#666}.artifact b{font-size:12px}.artifact p{font-size:10px;color:#999;margin:2px 0}.event{display:flex;gap:12px;border-top:1px solid #f0f0f2;padding:8px 0;font-size:10px}.event span{width:120px;color:#999}.event p{margin:0;color:#555}.composer{position:fixed;bottom:18px;left:calc(245px + 50%);transform:translateX(-50%);width:min(760px,calc(100% - 295px));display:flex;gap:7px;background:#fff;border:1px solid #ddd;border-radius:15px;padding:7px;box-shadow:0 8px 28px rgba(0,0,0,.08)}.composer textarea{flex:1;border:0;outline:0;resize:none;padding:9px;font:13px/1.5 inherit}.composer button{align-self:flex-end;border:0;border-radius:9px;padding:9px 13px;background:#18181b;color:#fff}.composer button:disabled{opacity:.4}@media(max-width:760px){.side{width:190px}.composer{left:calc(190px + 50%);width:calc(100% - 230px)}.chips{flex-wrap:wrap;justify-content:center}}
</style>
