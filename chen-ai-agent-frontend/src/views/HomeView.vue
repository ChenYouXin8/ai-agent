<script setup>
const apps = [
  {
    path: '/love',
    icon: '💕',
    name: 'AI 恋爱大师',
    desc: '聊天风格，模拟心理咨询师对话，帮你解答情感困惑',
    color: '#fce7f3',
    accent: '#ec4899',
  },
  {
    path: '/manus',
    icon: '🚀',
    name: 'AI 超级智能体',
    desc: '全能助手，可自主调用工具完成任务',
    color: '#ede9fe',
    accent: '#7c3aed',
  },
]
</script>

<template>
  <div class="home">
    <header class="home__header">
      <h1 class="home__title">AI 应用中心</h1>
      <p class="home__subtitle">选择一个 AI 应用，开始你的智能对话之旅</p>
    </header>

    <div class="home__cards">
      <router-link
        v-for="app in apps"
        :key="app.path"
        :to="app.path"
        class="card"
        :style="{ '--card-bg': app.color, '--card-accent': app.accent }"
      >
        <div class="card__icon">{{ app.icon }}</div>
        <div class="card__body">
          <h2 class="card__name">{{ app.name }}</h2>
          <p class="card__desc">{{ app.desc }}</p>
        </div>
        <span class="card__arrow">→</span>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.home {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: linear-gradient(135deg, #f5f7fb 0%, #ede9fe 50%, #fce7f3 100%);
}

.home__header {
  text-align: center;
  margin-bottom: 48px;
}

.home__title {
  font-size: 2rem;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 8px;
}

.home__subtitle {
  font-size: 1rem;
  color: var(--color-text-muted);
}

.home__cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 24px;
  width: 100%;
  max-width: 720px;
}

.card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  border: 2px solid transparent;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
  cursor: pointer;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border-color: var(--card-accent);
}

.card__icon {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  background: var(--card-bg);
  border-radius: var(--radius-md);
}

.card__body {
  flex: 1;
}

.card__name {
  font-size: 1.125rem;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--color-text);
}

.card__desc {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.card__arrow {
  flex-shrink: 0;
  font-size: 1.25rem;
  color: var(--card-accent);
  transition: transform 0.2s;
}

.card:hover .card__arrow {
  transform: translateX(4px);
}
</style>
