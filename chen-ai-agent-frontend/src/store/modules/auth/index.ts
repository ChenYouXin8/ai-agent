import { defineStore } from 'pinia'
import { getToken, removeToken, setToken } from './helper'
import { store } from '@/store/helper'

interface SessionResponse {
  auth: boolean
  model: 'ChatGPTAPI' | 'ChatGPTUnofficialProxyAPI'
}

export interface AuthState {
  token: string | undefined
  session: SessionResponse | null
}

// 本项目对接自有 Spring Boot 后端，无需登录。
// 这里固定为“无需鉴权 + ChatGPTAPI 模式”，避免触发权限弹窗。
const LOCAL_SESSION: SessionResponse = { auth: false, model: 'ChatGPTAPI' }

export const useAuthStore = defineStore('auth-store', {
  state: (): AuthState => ({
    token: getToken(),
    session: LOCAL_SESSION,
  }),

  getters: {
    isChatGPTAPI(state): boolean {
      return state.session?.model === 'ChatGPTAPI'
    },
  },

  actions: {
    async getSession() {
      this.session = { ...LOCAL_SESSION }
      return Promise.resolve(LOCAL_SESSION)
    },

    setToken(token: string) {
      this.token = token
      setToken(token)
    },

    removeToken() {
      this.token = undefined
      removeToken()
    },
  },
})

export function useAuthStoreWithout() {
  return useAuthStore(store)
}
