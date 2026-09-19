import { defineStore } from 'pinia'
import { TaskApi, loadScope, type ChenTask, type TaskScope } from '@/api/tasks'

/**
 * ChenManus 工作区共享状态：
 * 任务列表同时被统一外壳侧栏（Shell）与工作区主内容使用，
 * 选中任务通过路由 query（?task=xxx）表达，这里只缓存列表与 scope。
 */
export const useWorkspaceStore = defineStore('workspace-store', {
  state: () => ({
    scope: loadScope() as TaskScope,
    tasks: [] as ChenTask[],
    loading: false,
  }),

  getters: {
    recentTasks(state): ChenTask[] {
      return state.tasks.slice(0, 30)
    },
  },

  actions: {
    async loadTasks() {
      this.loading = true
      try {
        this.tasks = await TaskApi.list(this.scope)
      }
      catch (error) {
        console.error('加载任务失败', error)
      }
      finally {
        this.loading = false
      }
    },
  },
})
