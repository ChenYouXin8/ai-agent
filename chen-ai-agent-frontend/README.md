# Chen AI Agent 前端

> Chen AI Agent 的唯一前端：**ChenManus 任务工作区**。用聊天式界面创建任务，实时展示规划、执行、审核、补救与人工审批的完整过程。
> 界面基于开源项目 [Chanzhaoyu/chatgpt-web](https://github.com/Chanzhaoyu/chatgpt-web)（Vue 3 + TypeScript + Naive UI + Pinia + Tailwind，MIT 协议）二次开发。

## 功能

- 左侧栏：品牌区、新建任务、任务历史列表（按状态展示）、用户与设置入口。
- 主区域：空态推荐 Prompt、任务事件时间线（规划步骤、Agent 交接、工具调用、指标、审核、补救）、结果复制。
- 底部输入框：自然语言创建任务，支持优先级选择。
- 实时事件：原生 `EventSource` 订阅 SSE，断线重连后自动拉取历史事件合并。
- 任务控制：暂停、恢复、取消；高风险操作的批准 / 驳回。
- 配额角标：展示当前租户活跃任务数 / 配额。
- 设置弹窗：通用（头像、昵称、描述、主题、语言）与关于两个页签。

## 技术栈

- Vue 3（`<script setup>`）+ TypeScript
- Vue Router 4（hash 路由，默认进入 `#/workspace`）、Pinia 2
- Naive UI 2、Tailwind CSS 3
- markdown-it + KaTeX + highlight.js + Mermaid（结果 Markdown 渲染）
- Vite 4 构建，原生 `EventSource` / `fetch` 处理 SSE

## 目录说明

```
src/
├── api/tasks.ts            # 任务工作区 API 客户端（REST + SSE）与类型
├── views/
│   ├── workspace/          # ChenManus 工作区（默认且唯一的业务页面）
│   ├── shell/              # 应用外壳：侧边栏、用户底栏
│   └── exception/          # 404 / 500
├── components/common/      # NaiveProvider、SvgIcon、Setting 设置弹窗
├── router/                 # 路由（/ 重定向到 /workspace）
├── store/modules/          # app / user / workspace 三个 Pinia store
├── locales/                # 中英文案
└── styles/、icons/、hooks/、utils/
```

## 本地开发

### 1. 启动依赖服务

前端需要后端（Spring Boot，默认 `http://localhost:8123`，context-path `/api`）支持；任务记忆还需要 Chroma（默认 `http://127.0.0.1:8000`，未启动时自动降级）。

```bash
# Chroma 向量库（在仓库根目录）
chroma run --host 127.0.0.1 --port 8000 --path ./.chroma

# 后端（在仓库根目录，local profile）
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=local
```

### 2. 启动前端

```bash
cd chen-ai-agent-frontend
npm install
npm run dev
```

打开 http://127.0.0.1:5173/ ，自动进入 `#/workspace`。

Vite 已配置代理：`/api` → `http://localhost:8123`（不 rewrite），见 `vite.config.ts`。

### 环境变量（`.env`）

| 变量 | 说明 |
| --- | --- |
| `VITE_GLOB_API_URL` | 接口前缀，默认 `/api`（走 Vite 代理） |
| `VITE_APP_API_BASE_URL` | 后端地址，默认 `http://localhost:8123` |

## 构建与预览

```bash
npm run dev          # 开发
npm run type-check   # vue-tsc 类型检查
npm run build        # 构建产物到 dist/
npm run preview      # 预览构建产物
```

## 说明

- 任务接口统一响应体为 `{ code, message, data, time }`，`code === 0` 为成功。
- 租户 / 用户 / 会话标识保存在浏览器本地（localStorage / sessionStorage），用于请求的身份范围过滤。
- 高德 / Pexels 等 MCP 工具若后端未配置真实 Key，对应工具调用会失败，不影响普通任务。

## 致谢

界面基于 [Chanzhaoyu/chatgpt-web](https://github.com/Chanzhaoyu/chatgpt-web)（MIT）开发，感谢原作者。

## 许可证

MIT © ChenYouXin
