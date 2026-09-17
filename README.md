# chen-ai-agent

**基于 Spring Boot + Spring AI 的恋爱心理 AI 助手与通用 Agent 运行时平台**，集成通义千问大模型、Chroma 向量数据库、RAG 知识库问答、AI 工具调用、MCP、任务持久化、Reviewer、长期记忆与并行子 Agent。

## ChenManus 2.0 / 2.3

ChenManus 2.x 在保留原 `/manus` Agent 能力的同时，新增完整的任务运行时：

- **LLM Planner**：根据用户目标动态生成 2~6 个可执行步骤，并使用 Spring AI structured output 校验结构。
- **Step Runtime**：每个步骤由独立 ChenManus 执行，并携带前序步骤结果和会话历史记忆。
- **并行子 Agent**：Planner 可将互相独立的步骤标记为 `parallelizable=true`，Runtime 自动并发启动多个 ChenManus，再汇总进入后续步骤。
- **自动重试**：单个步骤失败最多自动重试 2 次。
- **Reviewer**：任务完成后由独立 Reviewer 审核；审核不通过时自动创建一次 Repair Step 并二次审核。
- **Task Memory**：任务结果落盘到 `data/task-memory`，并按 `userId + sessionId` 隔离，用于后续 Planner/Agent 上下文参考。
- **Task Persistence**：JDBC + H2 保存任务、步骤、审核结果、用户/会话标识与 Artifact，应用重启后自动恢复历史任务。
- **Artifact**：自动识别 Agent 输出中的 PDF、DOCX、XLSX、CSV、图片、ZIP、TXT 等交付路径。
- **真实工具事件**：围绕 Spring AI `ToolCallback` 捕获 `TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED`，通过 SSE 实时推送。
- **Workspace**：新增 `/chenmanus` 工作区，展示任务、并行步骤、工具事件、Reviewer、产物和最终结果。

## 环境要求

- JDK 21
- Python 3.11+（运行 Chroma 向量库）
- [通义千问 API Key](https://dashscope.console.aliyun.com/)（必需）
- searchapi.io API Key（可选，网页搜索工具使用）

## 快速开始

```bash
# 启动 Chroma
pip install chromadb
chroma run --host 127.0.0.1 --port 8000

# 克隆
git clone https://github.com/ChenYouXin8/ai-agent.git
cd ai-agent

# 配置
cp .env.example .env
# 编辑 .env，填入 AI_DASHSCOPE_API_KEY

# 启动后端
./mvnw spring-boot:run -Dspring.profiles.active=local

# 启动前端（另一个终端）
cd chen-ai-agent-frontend
npm install
npm run dev
```

打开 `/chenmanus` 进入 ChenManus Workspace。

## ChenManus 任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tasks` | 创建并异步启动任务 |
| GET | `/api/tasks?userId=&sessionId=` | 按用户/会话获取任务 |
| GET | `/api/tasks/{taskId}` | 获取任务详情、步骤、Reviewer、产物 |
| POST | `/api/tasks/{taskId}/pause` | 暂停 |
| POST | `/api/tasks/{taskId}/resume` | 恢复 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时事件 |

创建任务：

```bash
curl -X POST http://localhost:8123/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"prompt":"研究 AI Agent 最近的发展，分别收集国内外资料，最后整理成 PDF 报告","userId":"demo-user","sessionId":"demo-session"}'
```

监听实时事件：

```bash
curl -N http://localhost:8123/api/tasks/task_xxxxxxxxxxxx/events
```

## Runtime 架构

```text
User
 ↓
TaskController
 ↓
TaskManager ─────────→ H2 / JDBC
 ↓
LLM Planner ←───────── Task Memory
 ↓
Plan / PlanStep
 ↓
┌─────────────────────────────────────┐
│ Sequential / Parallelizable Steps   │
│                                     │
│ ChenManus #1 → Tools / MCP / RAG    │
│ ChenManus #2 → Tools / MCP / RAG    │  ← parallel
│ ChenManus #3 → Tools / MCP / RAG    │
└─────────────────────────────────────┘
 ↓
ArtifactService
 ↓
TaskReviewerService
 ├── PASS
 └── REPAIR → ChenManus → re-review
 ↓
Task Memory
 ↓
Final Result
```

### PlanStep

```text
PlanStep
├── title
├── description
├── type
├── expectedOutput
└── parallelizable
```

`parallelizable=true` 只用于彼此独立、没有前置依赖的步骤，例如多个来源的资料收集。汇总、写文件、代码修改和最终审核默认保持串行。

### Task 状态

```text
CREATED
  ↓
PLANNING
  ↓
RUNNING
  ├── STEP_STARTED
  ├── TOOL_STARTED
  ├── TOOL_COMPLETED / TOOL_FAILED
  ├── STEP_RETRY
  └── STEP_COMPLETED / STEP_FAILED
  ↓
REVIEWING
  ├── REVIEW_COMPLETED
  └── Repair Step（最多 1 轮）
  ↓
COMPLETED / FAILED / CANCELLED
```

## 原有 AI 能力

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai/chat` | 通用对话 |
| GET | `/api/ai/manus/chat` | Classic Manus SSE |
| GET | `/api/ai/love/chat` | 恋爱专家对话 |
| POST | `/api/ai/love/report` | 结构化恋爱报告 |
| GET | `/api/ai/love/rag` | RAG 知识库问答 |
| GET | `/api/ai/love/tools` | 恋爱专家 + 内置工具 |
| GET | `/api/ai/love/mcp` | 恋爱专家 + MCP |

## 持久化

默认使用 JDBC + H2，任务数据表：

- `chen_tasks`
- `chen_task_steps`
- `chen_task_artifacts`

长期任务记忆默认写入 `./data/task-memory`。生产部署时可以替换为 PostgreSQL/MySQL + 分布式任务队列，而无需改变任务 API。

## CI

GitHub Actions 会检查：

```text
Backend
├── Maven compile/package
└── offline unit tests

Frontend
├── npm ci
└── npm run build
```

依赖真实模型、第三方服务或外部网络的集成测试不作为默认离线 CI 门槛。

## 项目结构

```text
chen-ai-agent/
├── src/main/java/.../chenaiagent/
│   ├── agent/                    # BaseAgent / ReAct / ToolCall / ChenManus
│   ├── planner/                  # LLM Planner / Plan / PlanStep
│   ├── task/                     # Runtime / Persistence / Reviewer / Memory / Artifact
│   ├── controller/               # REST API
│   ├── tools/                    # Built-in tools
│   ├── app/                      # LoveApp
│   ├── chatmemory/               # Conversation memory
│   └── config/                   # Spring / Security / VectorStore
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql
│   └── mcp-servers.json
├── chen-ai-agent-frontend/       # Vue 3 Workspace
├── chen-image-search-mcp-server/ # MCP 子模块
├── .github/workflows/ci.yml
├── Dockerfile
├── Dockerfile.frontend
└── docker-compose.yml
```

## 后续演进

- PostgreSQL + Redis + 分布式锁与任务队列
- 向量化长期记忆与语义检索
- Researcher / Coder / Writer / Reviewer 等角色化 Agent
- 完整 DAG 依赖图、并发配额与资源预算
- Artifact 下载、预览和版本管理
- token、成本、耗时、工具成功率与任务质量指标
