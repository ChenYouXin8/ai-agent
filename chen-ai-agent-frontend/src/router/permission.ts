import type { Router } from 'vue-router'
import { useAuthStoreWithout } from '@/store/modules/auth'

// 本项目为本地/自有后端，不依赖 chatgpt-web 自带的 Node 服务，
// 因此跳过 /session 鉴权探测，直接标记为“无需登录”，避免跳转 500。
export function setupPageGuard(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    const authStore = useAuthStoreWithout()
    if (!authStore.session) {
      authStore.session = { auth: false, model: 'ChatGPTAPI' }
    }
    if (to.path === '/500')
      next({ name: 'Root' })
    else
      next()
  })
}
