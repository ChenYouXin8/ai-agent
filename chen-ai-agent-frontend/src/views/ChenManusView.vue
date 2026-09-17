<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ENDPOINTS } from '../config/api.js'

const tasks = ref([])
const current = ref(null)
const prompt = ref('')
const loading = ref(false)
let eventSource

async function loadTasks() {
  const body = await (await fetch(ENDPOINTS.tasks)).json()
  tasks.value = body.data || []
}
async function openTask(id) {
  current.value = (await (await fetch(ENDPOINTS.task(id))).json()).data
  eventSource?.close()
  eventSource = new EventSource(ENDPOINTS.taskEvents(id))
  const refresh = () => openTask(id)
  ;['plan_created','step_started','step_completed','step_failed','task_paused','task_resumed','task_cancelled','task_completed','task_failed'].forEach(n => eventSource.addEventListener(n, refresh))
}
async function createTask() {
  if (!prompt.value.trim() || loading.value) return
  loading.value = true
  try {
    const body = await (await fetch(ENDPOINTS.tasks, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({prompt:prompt.value.trim()}) })).json()
    current.value = body.data; prompt.value = ''; await loadTasks(); if (current.value?.taskId) openTask(current.value.taskId)
  } finally { loading.value = false }
}
async function action(name) { await fetch(ENDPOINTS[name](current.value.taskId), {method:'POST'}); await openTask(current.value.taskId) }
onMounted(loadTasks)
onBeforeUnmount(() => eventSource?.close())
</script>

<template>
  <div class="cm">
    <aside class="side"><div class="logo"><b>✦</b><span>ChenManus <i>2.0</i></span></div><button class="new" @click="current=null">＋ 新建任务</button><small class="label">最近任务</small><button v-for="t in tasks" :key="t.taskId" class="history" :class="{active:current?.taskId===t.taskId}" @click="openTask(t.taskId)"><em :class="t.status?.toLowerCase()"></em>{{t.title||t.prompt}}</button></aside>
    <main class="main"><header><div><small>AGENT WORKSPACE</small><h1>{{current?.title||'ChenManus 2.0'}}</h1></div><router-link to="/">返回</router-link></header>
      <section v-if="!current" class="empty"><div>✦</div><h2>把任务交给 ChenManus</h2><p>自主规划 · 工具调用 · 实时执行 · 最终交付</p><div class="chips"><button @click="prompt='研究 AI Agent 最近的发展并整理成报告'">研究主题</button><button @click="prompt='分析这个 Java 项目的代码问题'">分析代码</button><button @click="prompt='整理一份 PDF 报告'">生成文档</button></div></section>
      <section v-else class="content"><div class="bar"><span>{{current.status}}</span><button v-if="current.status==='RUNNING'" @click="action('taskPause')">暂停</button><button v-if="current.status==='PAUSED'" @click="action('taskResume')">继续</button><button v-if="!['COMPLETED','FAILED','CANCELLED'].includes(current.status)" @click="action('taskCancel')">取消</button></div><article><h3>执行计划 <small>{{current.steps?.length||0}} steps</small></h3><div v-for="s in current.steps" :key="s.stepId" class="step"><strong>{{s.status==='COMPLETED'?'✓':s.status==='RUNNING'?'●':'○'}}</strong><div><b>{{s.title}}</b><p>{{s.description}}</p><pre v-if="s.output">{{s.output}}</pre></div></div></article><article v-if="current.result"><h3>最终结果</h3><pre>{{current.result}}</pre></article></section>
      <form class="composer" @submit.prevent="createTask"><textarea v-model="prompt" rows="2" :disabled="loading" placeholder="描述你想完成的任务……"></textarea><button :disabled="loading||!prompt.trim()">{{loading?'创建中…':'开始任务 ↑'}}</button></form>
    </main>
  </div>
</template>

<style scoped>
.cm{height:100%;display:flex;background:#f7f7f8;color:#18181b}.side{width:245px;background:#fff;border-right:1px solid #e5e5e7;padding:20px 13px}.logo{display:flex;align-items:center;gap:9px;padding:0 7px 20px;font-size:17px}.logo b{display:grid;place-items:center;width:31px;height:31px;border-radius:9px;background:#18181b;color:#fff}.logo i{font-style:normal;font-size:10px;color:#999}.new{width:100%;border:0;border-radius:9px;padding:11px;text-align:left;background:#18181b;color:#fff}.label{display:block;color:#999;font-size:10px;text-transform:uppercase;margin:20px 7px 6px}.history{display:flex;align-items:center;gap:8px;width:100%;border:0;background:transparent;text-align:left;padding:10px 7px;border-radius:8px;color:#555;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.history.active,.history:hover{background:#f1f1f3}.history em{width:7px;height:7px;border-radius:50%;background:#ccc;flex:none}.history em.running{background:#18181b}.main{flex:1;min-width:0;display:flex;flex-direction:column}header{height:68px;background:#fff;border-bottom:1px solid #e5e5e7;padding:0 30px;display:flex;align-items:center;justify-content:space-between}header small{font-size:9px;letter-spacing:1.5px;color:#999}header h1{font-size:16px;margin:2px 0}header a{font-size:12px;color:#777;text-decoration:none}.empty{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center}.empty>div{font-size:42px}.empty h2{font-size:28px;margin:12px 0 7px}.empty p{color:#888}.chips{display:flex;gap:8px;margin-top:24px}.chips button{border:1px solid #ddd;background:#fff;border-radius:20px;padding:8px 13px;color:#555}.content{flex:1;overflow:auto;padding:26px max(22px,9vw) 130px}.bar{display:flex;gap:7px;align-items:center;margin-bottom:13px}.bar span{font-size:10px;background:#e9e9ec;border-radius:20px;padding:5px 8px}.bar button{background:#fff;border:1px solid #ddd;border-radius:7px;padding:5px 9px;font-size:12px}article{background:#fff;border:1px solid #e5e5e8;border-radius:13px;padding:17px;margin-bottom:13px}article h3{font-size:14px;margin:0 0 12px}article h3 small{font-weight:400;color:#999;font-size:10px}.step{display:flex;gap:12px;border-top:1px solid #f0f0f2;padding:12px 0}.step>strong{width:18px;text-align:center;color:#777}.step b{font-size:13px}.step p{font-size:11px;color:#999;margin:3px 0}.step pre,article>pre{white-space:pre-wrap;max-height:220px;overflow:auto;font:12px/1.6 inherit;color:#555}.composer{position:fixed;bottom:18px;left:calc(245px + 50%);transform:translateX(-50%);width:min(750px,calc(100% - 295px));display:flex;gap:7px;background:#fff;border:1px solid #ddd;border-radius:15px;padding:7px;box-shadow:0 8px 28px rgba(0,0,0,.08)}.composer textarea{flex:1;border:0;outline:0;resize:none;padding:9px;font:13px/1.5 inherit}.composer button{align-self:flex-end;border:0;border-radius:9px;padding:9px 13px;background:#18181b;color:#fff}.composer button:disabled{opacity:.4}@media(max-width:760px){.side{width:190px}.composer{left:calc(190px + 50%);width:calc(100% - 230px)}.chips{flex-wrap:wrap;justify-content:center}}
</style>
