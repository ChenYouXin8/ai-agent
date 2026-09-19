import type { Router } from 'vue-router'

// 本项目对接自有后端，无登录流程；/500 仅作为异常兜底页，统一回到工作区。
export function setupPageGuard(router: Router) {
  router.beforeEach((to, _from, next) => {
    if (to.path === '/500')
      next({ path: '/workspace' })
    else
      next()
  })
}
