# chen-ai-agent

**基于 Spring Boot + Spring AI 的恋爱心理 AI 助手与通用 Agent 运行时平台**。

ChenManus 2.x 在保留原 `/manus` 能力的同时，增加任务规划、逐步执行、Reviewer、长期记忆、Artifact、并行子 Agent、DAG 调度、队列、配额与安全租户边界。

## ChenManus 2.7

- **LLM Planner**：使用 Spring AI structured output 动态生成 2~6 个执行节点。
- **DAG Runtime**：每个节点支持 `dependsOn`，只有依赖完成后才进入 ready；独立节点可以并发执行。
- **并发配额**：`CHENMANUS_MAX_PARALLEL_STEPS` 限制单实例并行 Step 数。
- **Redis Task Queue**：生产环境可使用 Redis 任务队列，多实例 worker 通过队列分发任务；本地可回退到内存队列。
- **Redis Distributed Lock**：同一任务使用分布式锁，避免多实例重复执行。
- **Crash Recovery**：应用启动时恢复未完成任务，任务步骤和指标从数据库继续。
- **PostgreSQL / H2**：本地默认 H2，Docker Compose 使用 PostgreSQL；保存逻辑采用 UPDATE → INSERT，避免绑定 H2 `MERGE` 语法。
- **租户级配额**：支持按 `tenantId` 限制进行中的任务数量与查询范围。
- **可信身份模式**：开发模式兼容 body/query 的 `tenantId/userId`；生产可由认证网关注入 `X-Tenant-Id / X-User-Id`，并开启可信身份校验。
- **租户级语义记忆隔离**：VectorStore 与文件回退都会带 tenant scope，避免不同租户之间互相召回任务记忆。
- **Reviewer + Repair**：结果由独立 Reviewer 审核；不通过时自动生成一次补救步骤并二次审核。
- **角色化 Agent**：Researcher / Analyst / Coder / Writer / General。
- **真实工具事件**：通过 `ToolCallback` wrapper 捕获 TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED。
- **Metrics**：记录任务/步骤耗时、模型调用次数、实际/估算 token 与估算成本。
- **Artifact**：自动识别 PDF、DOCX、XLSX、CSV、图片、ZIP、TXT 等交付路径，并提供安全下载与浏览器预览 API。
- **Workspace**：`/chenmanus` 展示队列、DAG、优先级、并行步骤、Reviewer、Artifacts、工具事件和 Usage。

## 环境要求

- JDK 21
- Python 3.11+（Chroma）
- 通义千问 API Key
- searchapi.io API Key（可选）
- Docker Compose：PostgreSQL 17 + Redis 7 + Chroma

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

## 任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tasks` | 创建并进入任务队列 |
| GET | `/api/tasks?tenantId=&userId=&sessionId=` | 按租户/用户/会话查询任务 |
| GET | `/api/tasks/quota?tenantId=` | 查询租户当前任务配额 |
| GET | `/api/tasks/{taskId}` | 获取任务详情、DAG、Reviewer、Artifact、指标 |
| POST | `/api/tasks/{taskId}/pause` | 暂停 |
| POST | `/api/tasks/{taskId}/resume` | 恢复并重新入队 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时事件 |
| GET | `/api/tasks/{taskId}/artifacts/{artifactId}/download` | 下载 Artifact |
| GET | `/api/tasks/{taskId}/artifacts/{artifactId}/preview` | PDF / 图片 / 文本预览 |

## 生产身份

默认配置保持开发兼容：

```bash
CHENMANUS_TRUST_IDENTITY_HEADERS=false
CHENMANUS_REQUIRE_IDENTITY_HEADERS=false
```

接入认证网关后建议：

```bash
CHENMANUS_TRUST_IDENTITY_HEADERS=true
CHENMANUS_REQUIRE_IDENTITY_HEADERS=true
```

认证网关应在完成真实用户认证后注入：

```text
X-Tenant-Id: tenant-a
X-User-Id: user-123
```

开启后 task API 不再以 body/query 提供的身份覆盖可信请求头。直接暴露应用且允许客户端自行设置这些头并不能形成完整的身份认证，因此生产部署仍应由可信网关或认证层负责身份校验。

## Runtime 架构

```text
User / Auth Gateway
 ↓
Trusted Tenant + User Context
 ↓
TaskController
 ↓
TaskManager ───────────────→ PostgreSQL / H2
 ↓
Redis Queue → Worker → Distributed Lock
 ↓
LLM Planner ←────────────── Semantic Memory (tenant scoped)
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
Tenant-scoped Task Memory
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

`chen_tasks.tenant_id` 是任务隔离主键维度；`chen_task_steps.depends_on` 保存 DAG 依赖，`parallelizable` 控制可并行节点。

## Artifact 安全

Artifact 下载默认只允许读取 `CHENMANUS_ARTIFACT_ALLOWED_ROOT` 下的真实文件，并检查 real path，拦截路径穿越和软链接逃逸；HTTP/HTTPS 外部地址不会由后端代理下载，避免把 Artifact API 变成 SSRF 代理。

默认：

```bash
CHENMANUS_ARTIFACT_ALLOWED_ROOT=./data
```

## 配置

```bash
# Redis 队列与分布式锁
CHENMANUS_REDIS_ENABLED=false
CHENMANUS_MAX_PARALLEL_STEPS=4

# 租户配额
CHENMANUS_MAX_ACTIVE_TASKS_PER_TENANT=20

# 可信身份
CHENMANUS_TRUST_IDENTITY_HEADERS=false
CHENMANUS_REQUIRE_IDENTITY_HEADERS=false

# Artifact 安全根目录
CHENMANUS_ARTIFACT_ALLOWED_ROOT=./data

# 成本估算，默认 0，仅记录 token
CHENMANUS_INPUT_COST_PER_1K=0.0
CHENMANUS_OUTPUT_COST_PER_1K=0.0
```

## CI

GitHub Actions 会执行后端 Maven compile/package、离线单元测试和前端 npm build。当前离线测试覆盖持久化、配额、队列、指标、Artifact、可信身份和租户级 Memory 隔离；依赖真实模型、第三方 API 或外网服务的集成测试不作为默认离线 CI 门槛。

## 兼容性

旧 `/api/ai/*` 接口和 `/manus` Classic 页面继续保留；ChenManus 2.x 通过新的 `/api/tasks` 与 `/chenmanus` Workspace 提供任务化运行时。

## 下一层

- Redis Streams / 延迟队列与死信队列
- 真正的模型 Usage / provider bill 核算
- 多 Agent 动态 Team Planner 与 Agent-to-Agent handoff
- Artifact 对象存储、版本历史与在线编辑
- OAuth2/OIDC / JWT 与细粒度 RBAC

## 许可证

MIT License
