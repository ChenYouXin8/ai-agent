// 本项目前端统一对接自有 Spring Boot 后端，流式对话见 ./chat.ts。
// 以下仅保留 chatgpt-web 旧组件（权限弹窗 / 关于面板）引用的接口，
// 本地无鉴权场景下直接返回本地结果，避免请求不存在的 Node 服务。

export function fetchChatConfig<T = any>() {
  return Promise.resolve({
    data: {
      apiModel: 'ChatGPTAPI',
      reverseProxy: '',
      timeout: 300000,
      socksProxy: '',
      httpsProxy: '',
    },
  } as { data: T })
}

export function fetchVerify<T = any>(_token: string) {
  // 本地无鉴权，直接视为通过
  return Promise.resolve({ data: { status: 'Success' } as unknown as T })
}
