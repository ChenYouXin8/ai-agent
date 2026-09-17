# chen-ai-agent

**基于 Spring Boot + Spring AI 的恋爱心理 AI 助手与通用 Agent 运行时平台**。

ChenManus 2.x 在保留原 `/manus` 能力的同时，增加任务规划、逐步执行、Reviewer、长期记忆、Artifact、并行子 Agent、DAG 调度与可观测性。

## ChenManus 2.5

- **LLM Planner**：使用 Spring AI structured output 动态生成 2~6 个执行节点。
- **DAG Runtime**：每个节点支持 `dependsOn`，只有依赖完成后才进入 ready；无依赖或已满足依赖的节点可以并发执行。
- **并发配额**：`CHENMANUS_MAX_PARALLEL_STEPS` 默认限制单实例并行 Step 数，避免瞬时占满模型连接和线程池。
- **Redis Task Queue**：生产环境使用 Redis 任务队列，多实例 worker 通过队列分发任务。
- **Redis Distributed Lock**：同一任务使用分布式锁，避免多实例重复执行。
- **Crash Recovery**：应用启动时会把 QUEUED / PLANNING / RUNNING / REVIEWING 任务重新入队；任务步骤状态会从数据库恢复。
- **PostgreSQL / H2**：默认本地使用 H2 文件数据库，Docker Compose 使用 PostgreSQL；任务保存采用数据库无关的 UPDATE → INSERT。
- **Reviewer + Repair**：结果由独立 Reviewer 审核；不通过时自动生成一次补救步骤并二次审核。
- **角色化 Agent**：Researcher / Analyst / Coder / Writer / General。
- **语义长期记忆**：任务记忆写入 VectorStore，并按 `ownerId + sessionId` 隔离；不可用时回退文件检索。
- **真实工具事件**：通过 `ToolCallback` wrapper 捕获 TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED。
- **Metrics**：记录任务/步骤耗时与估算 token；成本按 `CHENMANUS_*_COST_PER_1K` 配置计算。token 与成本是估算值，不代表模型厂商账单。
- **Artifact**：自动识别 PDF、DOCX、XLSX、CSV、图片、ZIP、TXT 等交付路径。
- **Workspace**：`/chenmanus` 展示任务队列状态、DAG 依赖、并行步骤、Reviewer、Artifacts、工具事件和指标。

## 环境要求

- JDK 21
- Python 3.11+（Chroma）
- 通义千问 API Key
- searchapi.io API Key（可选）
- Docker Compose 生产部署：PostgreSQL 17 + Redis 7 + Chroma

## 本地开发

```bash
pip install chromadb
chroma run --host 127.0.0.1 --port 8000

git clone https://github.com/ChenYouXin8/ai-agent.git
cd ai-agent
cp .env.example .env
./mvnw spring-boot:run -Dspring.profiles.active=local
```

前端：

```bash
cd chen-ai-agent-frontend
npm install
npm run dev
```

打开 `/chenmanus`。

## Docker Compose

```bash
cp .env.example .env
# 填入 AI_DASHSCOPE_API_KEY 与 POSTGRES_PASSWORD

docker compose up -d --build
```

默认服务：

```text
Frontend     :5173
Backend      :8123
PostgreSQL  :5432
Redis        :6379
Chroma       :8000
```

Docker 下后端会自动连接 PostgreSQL 与 Redis，并启用 Redis 队列和分布式锁。

## 任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tasks` | 创建并进入任务队列 |
| GET | `/api/tasks?userId=&sessionId=` | 按用户/会话查询任务 |
| GET | `/api/tasks/{taskId}` | 获取任务详情、DAG、Reviewer、Artifact、指标 |
| POST | `/api/tasks/{taskId}/pause` | 暂停 |
| POST | `/api/tasks/{taskId}/resume` | 恢复并重新入队 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时事件 |

创建任务：

```bash
curl -X POST http://localhost:8123/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"prompt":"研究 AI Agent 最近的发展，收集多个来源后整理成报告","userId":"demo-user","sessionId":"demo-session"}'
```

## Runtime 架构

```text
User
 ↓
TaskController
 ↓
TaskManager ───────────────→ PostgreSQL / H2
 ↓
Redis Queue → Worker → Distributed Lock
 ↓
LLM Planner ←────────────── Semantic Memory
 ↓
Execution DAG
 ├── Researcher ─→ ChenManus ─→ Tools / MCP
 ├── Researcher ─→ ChenManus ─→ Tools / MCP   ← parallel
 ├── Analyst    ─→ ChenManus
 └── Coder      ─→ ChenManus ─→ Files
 ↓
Artifact + Metrics
 ↓
Reviewer
 ├── PASS
 └── REPAIR → Agent → Re-review
 ↓
Task Memory
 ↓
Final Result
```

## 角色路由

```text
RESEARCH / WEB / SEARCH     → Researcher
ANALYSIS                    → Analyst
CODE / JAVA / VUE           → Coder
DOCUMENT / REPORT / WRITE   → Writer
其它                         → General
```

## 数据表

- `chen_tasks`
- `chen_task_steps`
- `chen_task_artifacts`

`chen_task_steps.depends_on` 保存 DAG 依赖，`parallelizable` 控制可并行节点。

## 配置

```bash
# Redis 队列与分布式锁
CHENMANUS_REDIS_ENABLED=false
CHENMANUS_MAX_PARALLEL_STEPS=4

# 成本估算，默认 0，仅记录估算 token
CHENMANUS_INPUT_COST_PER_1K=0.0
CHENMANUS_OUTPUT_COST_PER_1K=0.0
```

## CI

GitHub Actions 会执行后端 Maven compile/package、离线单元测试和前端 npm build。依赖真实模型、第三方 API 或外网服务的集成测试不作为默认离线 CI 门槛。

## 兼容性

旧 `/api/ai/*` 接口和 `/manus` Classic 页面继续保留；ChenManus 2.x 通过新的 `/api/tasks` 与 `/chenmanus` Workspace 提供任务化运行时。

## 下一层

- Artifact 下载/预览/版本管理
- 任务优先级与租户配额
- Redis Streams / 延迟队列
- 真正的模型 Usage 统计与供应商成本核算
- 多 Agent 协作的动态 Team Planner

## 许可证

MIT License
