# Chen AI Agent 前端

> Chen AI Agent 的统一前端：**ChenManus 2.0 任务工作区**为默认主界面，左侧栏可进入「智能助手」「AI 恋爱大师」两个对话应用。
> 界面基于开源项目 [Chanzhaoyu/chatgpt-web](https://github.com/Chanzhaoyu/chatgpt-web)（Vue 3 + TypeScript + Naive UI + Pinia + Tailwind，MIT 协议）二次开发。

## 应用结构

打开应用默认进入 **ChenManus 2.0 任务工作区**（`#/workspace`），左侧栏统一切换三个应用：

| 应用 | 说明 | 后端接口 |
| --- | --- | --- |
| **ChenManus 2.0 工作区** | 任务编排与执行：DAG、Team Handoff、人工审批、Reviewer、用量统计、产物、实时/审计事件 | `POST/GET /api/tasks`、`GET /api/tasks/{id}/events`（SSE）等 |
| **智能助手** | ChenManus Classic ReAct 对话，会调用工具完成任务，流式输出 | `GET /api/ai/manus/chat?message=`（SSE） |
| **AI 恋爱大师** | 情感咨询对话，按会话维持上下文，流式输出 | `GET /api/ai/love/chat/sse?message=&chatId=`（SSE） |

- 工作区左侧栏点击「智能助手 / AI 恋爱大师」会打开对应应用**最近一次会话**，没有则新建。
- 对话页左侧栏顶部「ChenManus 工作区」按钮可回到主界面；「智能助手 / AI 恋爱大师」用于在两个对话应用之间切换；「新建聊天」在当前应用内新建会话。
- 会话数据保存在浏览器本地（localStorage），并按应用（`mode`）区分。

## 技术栈

- Vue 3（`<script setup>`）+ TypeScript
- Vue Router 4（hash 路由）、Pinia 2
- Naive UI 2、Tailwind CSS 3
- markdown-it + KaTeX + highlight.js + Mermaid（消息 Markdown 渲染）
- Vite 4 构建，原生 `EventSource` / `fetch` 处理 SSE

## 目录说明

```
src/
├── api/
│   ├── chat.ts          # 两个对话应用的 SSE 客户端（assistant / love）
│   └── tasks.ts         # ChenManus 2.0 任务工作区 API 客户端 + 类型
├── views/
│   ├── workspace/       # ChenManus 2.0 任务工作区（默认主界面）
│   └── chat/            # chatgpt-web 风格对话界面（两个对话应用共用）
├── store/modules/chat/  # 会话 store（含 mode、openApp 应用切换）
└── router/index.ts      # 路由（/ 重定向到 /workspace）
```

## 本地开发

### 1. 启动依赖服务

前端需要后端（Spring Boot，默认 `http://localhost:8123`，context-path `/api`）支持；
ChenManus 工作区与 RAG 能力还需要 Chroma 向量库（默认 `http://127.0.0.1:8000`）。

```bash
# Chroma 向量库（在仓库根目录）
chroma run --host 127.0.0.1 --port 8000 --path ./.chroma

# 后端（在仓库根目录，local profile 含本地模型 Key 配置）
$env:SPRING_PROFILES_ACTIVE='local'   # PowerShell
./mvnw spring-boot:run
```

### 2. 启动前端

```bash
cd chen-ai-agent-frontend
npm install
npm run dev
```

打开 http://127.0.0.1:5173/ ，会自动跳转到 `#/workspace`。

Vite 已配置代理：`/api` → `http://localhost:8123`（不 rewrite），见 `vite.config.ts`。

### 环境变量（`.env`）

| 变量 | 说明 |
| --- | --- |
| `VITE_GLOB_API_URL` | 接口前缀，默认 `/api`（走 Vite 代理） |
| `VITE_APP_API_BASE_URL` | 后端地址，默认 `http://localhost:8123` |

## 构建与预览

```bash
npm run build        # 类型检查通过后，产物输出到 dist/
npm run type-check   # 仅做 vue-tsc 类型检查
npm run preview      # 预览构建产物
```

## 说明与限制

- 任务接口统一响应体为 `{ code, message, data }`，`code === 0` 为成功。
- 租户配额接口 `GET /api/tasks/quota` 在后端 `legacy` 安全模式下默认关闭（fail-closed，返回 403），
  此时工作区仅不显示配额数字，不影响任务创建与执行；配置 `CHENMANUS_LEGACY_ADMIN_TOKEN` 后可恢复。
- 高德 / Pexels 等 MCP 工具若未配置真实 Key，对应工具会返回 `INVALID_USER_KEY`，不影响普通对话与任务工作区。

## 致谢

界面基于 [Chanzhaoyu/chatgpt-web](https://github.com/Chanzhaoyu/chatgpt-web)（MIT）开发，感谢原作者。

## 许可证

MIT © ChenYouXin
