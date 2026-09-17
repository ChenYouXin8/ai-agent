# chen-ai-agent

**基于 Spring Boot + Spring AI 的恋爱心理 AI 助手与通用 Agent 运行时平台**。

ChenManus 2.x 在保留原 `/manus` 能力的同时，增加任务规划、DAG 调度、并行子 Agent、Reviewer、长期记忆、Artifact、队列、配额、租户隔离、安全认证与 Agent-to-Agent handoff。

## ChenManus 2.8

- **LLM Planner**：使用 Spring AI structured output 动态生成 2~6 个执行节点。
- **DAG Runtime**：每个节点支持 `dependsOn`，依赖全部完成后才进入 ready；独立节点可以并发执行。
- **Team Planner**：为每个 DAG 节点动态分配 Researcher / Analyst / Coder / Writer / General 角色。
- **Agent Handoff**：节点执行前生成结构化 handoff context，并通过 `AGENT_HANDOFF` SSE 事件记录团队交接。
- **并发配额**：`CHENMANUS_MAX_PARALLEL_STEPS` 限制单实例并行 Step 数。
- **Redis Task Queue**：生产环境可使用 Redis 任务队列，多实例 worker 通过队列分发任务；本地可回退到内存队列。
- **Redis Distributed Lock**：同一任务使用分布式锁，避免多实例重复执行。
- **Dead Letter Queue**：Worker 异常或任务失败后进入 DLQ，管理员可以查看并 replay。
- **Crash Recovery**：应用启动时恢复未完成任务，任务步骤和指标从数据库继续。
- **PostgreSQL / H2**：本地默认 H2，Docker Compose 使用 PostgreSQL；保存逻辑采用 UPDATE → INSERT。
- **租户隔离**：任务、查询、配额与语义长期记忆按 tenant scope 隔离。
- **OAuth2/OIDC Resource Server**：可选 JWT 模式，从 JWT subject 和 tenant claim 获取身份；支持 `roles`、`realm_access.roles`、`permissions` 映射。
- **RBAC**：`TENANT_ADMIN` 可管理本租户任务，`PLATFORM_ADMIN` 可跨租户访问任务；普通用户只能访问自己的任务。
- **JWT 安全校验**：OAuth2 模式支持 issuer 校验，并可选校验 `aud` claim。
- **Reviewer + Repair**：结果由独立 Reviewer 审核；不通过时自动生成一次补救步骤并二次审核。
- **真实工具事件**：通过 `ToolCallback` wrapper 捕获 TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED。
- **Metrics**：记录任务/步骤耗时、模型调用次数、实际/估算 token 与估算成本。
- **Artifact**：自动识别 PDF、DOCX、XLSX、CSV、图片、ZIP、TXT 等交付路径，并提供安全下载与浏览器预览 API。
- **Artifact 安全**：下载只允许安全根目录下的真实文件，拦截路径穿越、软链接逃逸与 HTTP/HTTPS 外部代理。
- **Workspace**：`/chenmanus` 展示队列、DAG、优先级、Team Handoff、Reviewer、Artifacts、工具事件、Usage 与 DLQ 状态。

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
Redis       :6379
Chroma       :8000
```

## OAuth2 / OIDC

默认仍是 `legacy` 模式。生产环境可以使用 JWT Resource Server：

```bash
CHENMANUS_SECURITY_MODE=oauth2
CHENMANUS_OIDC_ISSUER_URI=https://idp.example.com/realms/chenmanus
CHENMANUS_OIDC_AUDIENCE=https://api.example.com
```

OAuth2 模式下：

- `sub` 或 `user_id` 提供用户身份。
- `tenant_id` 或 `tenant` 提供租户身份；缺失时拒绝请求，不落到默认租户。
- `roles`、`realm_access.roles`、`permissions` 映射为 Spring Security authorities。
- `TENANT_ADMIN` 可以访问本租户其它用户的任务；`PLATFORM_ADMIN` 可以跨租户访问任务。
- `aud` 校验只有配置 `CHENMANUS_OIDC_AUDIENCE` 时才启用。

Legacy + 可信网关模式可使用：

```bash
CHENMANUS_TRUST_IDENTITY_HEADERS=true
CHENMANUS_REQUIRE_IDENTITY_HEADERS=true
```

此时由网关注入 `X-Tenant-Id` / `X-User-Id`，服务端拒绝缺失身份头。

## 任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tasks` | 创建并进入任务队列 |
| GET | `/api/tasks?tenantId=&userId=&sessionId=` | 查询任务；OAuth2 模式身份来自 JWT |
| GET | `/api/tasks/quota?tenantId=` | 查询租户任务配额（OAuth2 需管理员角色） |
| GET | `/api/tasks/{taskId}` | 获取任务详情、DAG、Reviewer、Artifact、指标 |
| POST | `/api/tasks/{taskId}/pause` | 暂停 |
| POST | `/api/tasks/{taskId}/resume` | 恢复并重新入队 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时事件 |
| GET | `/api/tasks/{taskId}/artifacts/{artifactId}/download` | 下载 Artifact |
| GET | `/api/tasks/{taskId}/artifacts/{artifactId}/preview` | PDF / 图片 / 文本预览 |
| GET | `/api/tasks/admin/dlq` | 管理员查看 DLQ |
| POST | `/api/tasks/admin/dlq/replay` | 管理员重放 DLQ 任务 |

## Runtime 架构

```text
User / OIDC IdP / Auth Gateway
 ↓
JWT → Tenant + User + Roles
 ↓
TaskController
 ↓
TaskManager ───────────────→ PostgreSQL / H2
 ↓
Redis Queue → Worker → Distributed Lock
 ↓
LLM Planner ←────────────── Tenant-scoped Semantic Memory
 ↓
Execution DAG
 ├── Team Planner → Researcher → ChenManus → Tools / MCP
 ├── Team Planner → Researcher → ChenManus → Tools / MCP   ← parallel
 ├── Team Planner → Analyst    → ChenManus
 └── Team Planner → Coder      → ChenManus → Files
               ↑
          Agent Handoff
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

Failure → Dead Letter Queue → Admin Replay
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

`chen_tasks.tenant_id` 是任务隔离主维度；`chen_task_steps.depends_on` 保存 DAG 依赖，`parallelizable` 控制可并行节点。

## Artifact 安全

Artifact 下载默认只允许读取 `CHENMANUS_ARTIFACT_ALLOWED_ROOT` 下的真实文件，并检查 real path，拦截路径穿越和软链接逃逸；HTTP/HTTPS 外部地址不会由后端代理下载，避免把 Artifact API 变成 SSRF 代理。

默认：

```bash
CHENMANUS_ARTIFACT_ALLOWED_ROOT=./data
```

## 配置

```bash
CHENMANUS_REDIS_ENABLED=false
CHENMANUS_MAX_PARALLEL_STEPS=4
CHENMANUS_MAX_ACTIVE_TASKS_PER_TENANT=20

CHENMANUS_SECURITY_MODE=oauth2
CHENMANUS_OIDC_ISSUER_URI=https://idp.example.com/issuer
CHENMANUS_OIDC_AUDIENCE=https://api.example.com

CHENMANUS_TRUST_IDENTITY_HEADERS=false
CHENMANUS_REQUIRE_IDENTITY_HEADERS=false
CHENMANUS_ARTIFACT_ALLOWED_ROOT=./data

CHENMANUS_INPUT_COST_PER_1K=0.0
CHENMANUS_OUTPUT_COST_PER_1K=0.0
```

## CI

GitHub Actions 会执行后端 Maven compile/package、离线单元测试和前端 npm build。离线测试覆盖持久化、配额、队列/DLQ、指标、Artifact、角色路由、身份与租户隔离；依赖真实模型、第三方 API 或外网服务的集成测试不作为默认离线 CI 门槛。

## 兼容性

旧 `/api/ai/*` 接口和 `/manus` Classic 页面继续保留；ChenManus 2.x 通过新的 `/api/tasks` 与 `/chenmanus` Workspace 提供任务化运行时。

## 下一层

- Redis Streams / 延迟队列与消费组
- 真正的 provider Usage / bill 核算
- Agent Team 动态协作、人工介入与审批节点
- Artifact 对象存储、版本历史与在线编辑
- 更细粒度的 RBAC / ABAC 与审计日志

## 许可证

MIT License
