<script setup>
const apps = [
  { path: '/love', icon: '💕', name: 'AI 恋爱大师', desc: '情感对话与建议' },
  { path: '/chenmanus', icon: '✦', name: 'ChenManus 2.0', desc: '自主规划、工具调用、任务执行与交付' },
  { path: '/manus', icon: '🚀', name: 'ChenManus Classic', desc: '保留原版 Agent 对话入口' },
]
</script>

<template>
  <div class="home"><header><div class="mark">✦</div><h1>Chen AI</h1><p>AI 应用中心</p></header><div class="cards"><router-link v-for="app in apps" :key="app.path" :to="app.path" class="card"><span class="icon">{{app.icon}}</span><div><h2>{{app.name}}</h2><p>{{app.desc}}</p></div><b>→</b></router-link></div></div>
</template>

<style scoped>
.home{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:40px 24px;background:radial-gradient(circle at top,#fff 0,#f5f5f7 55%,#ededf0 100%)}header{text-align:center;margin-bottom:35px}.mark{margin:auto;width:52px;height:52px;border-radius:15px;background:#18181b;color:#fff;display:grid;place-items:center;font-size:25px}h1{font-size:28px;margin:14px 0 4px}header p{color:#999;margin:0}.cards{display:grid;gap:13px;width:min(620px,100%)}.card{display:flex;align-items:center;gap:15px;padding:18px;background:#fff;border:1px solid #e5e5e8;border-radius:14px;text-decoration:none;color:#18181b;box-shadow:0 5px 18px rgba(0,0,0,.04);transition:.2s}.card:hover{transform:translateY(-2px);box-shadow:0 10px 25px rgba(0,0,0,.08)}.icon{width:45px;height:45px;display:grid;place-items:center;border-radius:12px;background:#f1f1f3;font-size:23px}.card div{flex:1}.card h2{font-size:15px;margin:0 0 4px}.card p{font-size:12px;color:#888;margin:0}.card b{color:#aaa}
</style>
