# chen-ai-agent

**ChenManus 2.9** —— 基于 Spring Boot + Spring AI 的通用 Agent 运行时平台，内置一个「恋爱心理 AI 助手」示例应用。

平台包含两条能力线：

- **ChenManus 任务运行时**：把一句自然语言需求，交给 LLM 规划成 DAG，调度多个角色子 Agent 并行/串行执行，配合人工审批、Reviewer 验收、失败重试与补救、产物管理、审计追踪，最终交付可验证的结果。
- **恋爱心理助手（LoveApp）**：面向单身 / 恋爱 / 已婚三阶段的情感咨询专家，支持 RAG 知识库、结构化报告、多轮记忆、工具调用与 MCP 扩展。

---

## 目录

- [核心特性](#核心特性)
- [技术栈](#技术栈)
- [运行时架构](#运行时架构)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置项](#配置项)
- [前端页面](#前端页面)
- [HTTP API](#http-api)
- [内置工具](#内置工具)
- [MCP 外部工具](#mcp-外部工具)
- [人工审批与审计](#人工审批与审计)
- [安全与多租户](#安全与多租户)
- [CI](#ci)
- [兼容性说明](#兼容性说明)
- [License](#license)

---

## 核心特性

### ChenManus 任务运行时

- **LLM Planner**：由大模型输出结构化执行计划（标题、类型、预期输出、依赖关系、是否可并行、是否需审批），构建任务 DAG。
- **DAG 调度与并行执行**：按依赖关系自动调度；无依赖且标记为 `parallelizable` 的步骤通过信号量并发执行（默认上限 4，可配置）。
- **多角色子 Agent（Team Planner）**：步骤按类型分配给 `RESEARCHER` / `ANALYST` / `CODER` / `WRITER` / `GENERAL` 角色，Agent 之间通过 Handoff 上下文交接依赖结果。
- **人工审批（Human-in-the-loop）**：部署、购买、支付等高风险步骤在执行前暂停，任务进入 `WAITING_USER`，经批准 / 驳回后继续或终止。
- **Reviewer 验收与自修复**：步骤全部完成后由 Reviewer 审核；未通过时自动生成补救步骤并进行二次验收（最多 1 轮修复、每步最多重试 2 次）。
- **持久化与断点恢复**：任务、步骤、产物、事件全部落库（默认 H2 文件库，可切换 PostgreSQL），每次状态变更原子写入，应用重启后可恢复。
- **任务队列与死信队列**：本地队列或 Redis 分布式队列（按优先级），Worker 轮询执行；失败任务进入 DLQ，管理员可重放。
- **分布式锁**：开启 Redis 后，任务执行与调度加分布式锁，支持多实例水平扩展；锁获取 fail-closed，Lua CAS 原子解锁。
- **租户配额**：按租户限制活跃任务数（默认 20）。
- **定时任务模板**：支持任务模板与 Cron 调度（`chen_task_templates` / `chen_task_schedules`）。
- **产物管理（Artifact）**：从 Agent 输出中捕获文件产物，支持安全下载与预览，路径限定在允许根目录内。
- **用量与成本指标**：记录每个任务 / 步骤的 token 用量、模型调用次数、耗时与估算成本。
- **作用域记忆**：按租户 / 用户 / 会话隔离的语义向量记忆，规划与执行时召回相关历史。
- **实时事件流**：SSE 推送规划、步骤、工具、审批、审查、指标等 26 类事件，并提供可过滤的持久化审计历史。

### LoveApp 恋爱心理助手

- **恋爱心理专家**：内置系统提示词，按单身 / 恋爱 / 已婚三阶段引导对话。
- **RAG 知识库问答**：基于 Chroma 向量库检索恋爱知识文档（单身篇 / 恋爱篇 / 已婚篇）增强回答。
- **结构化报告**：模型按 `LoveReport` 结构输出标题与建议列表。
- **多轮对话记忆**：Kryo 文件持久化，按 `chatId` 隔离会话。
- **工具调用与 MCP**：支持 7 个内置工具与高德地图、Pexels 图片搜索等 MCP 服务。

---

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 / 框架 | Java 21、Spring Boot 4.1.0、Spring AI 2.0.0 |
| 大模型 | 通义千问（DashScope OpenAI 兼容接口），`qwen-max`；Embedding `text-embedding-v3` |
| 向量库 | Chroma（RAG 知识库 / 语义记忆） |
| 关系库 | H2 文件库（默认，零依赖启动）/ PostgreSQL 17（生产） |
| 缓存 / 队列 | Redis 7（分布式队列、分布式锁，可选） |
| Agent 协议 | MCP（Model Context Protocol）客户端 |
| 文档 | Spring AI Markdown Reader、iText 9（PDF）、Kryo（记忆持久化） |
| 接口文档 | Knife4j / springdoc OpenAPI 3 |
| 前端 | Vue 3、Vite、Vue Router（Nginx 部署） |
| 部署 | Docker Compose（PostgreSQL + Redis + Chroma + 后端 + 前端） |

---

## 运行时架构

```text
用户 / OIDC / 可信网关
        ↓
身份解析 → 租户 + 用户 + 角色
        ↓
   TaskController  ──→  配额校验
        ↓
 H2 / PostgreSQL（任务聚合 + 事件审计）
        ↓
 任务队列（本地 / Redis）→ Worker → 分布式锁
        ↓
 LLM Planner → 审批策略 → 执行 DAG
        ↓
 Team Planner → Agent Handoff → ChenManus 子 Agent（工具 / MCP）
        ↓
   产物捕获 + 用量指标
        ↓
 Reviewer 审核 → 补救 / 通过
        ↓
 作用域记忆 → 最终结果

异常 → 死信队列（DLQ）→ 管理员重放
```

任务状态机：

```text
CREATED → QUEUED → PLANNING → RUNNING ──→ REVIEWING ──→ COMPLETED
                        │   ↑  │            │
                        │   └──┴──── WAITING_USER（人工审批）
                        ├→ PAUSED           └→ 补救步骤 → 二次审核
                        └→ FAILED / CANCELLED
```

---

## 项目结构

```text
chen-ai-agent/
├── src/main/java/io/github/chenyouxin8/chenaiagent/
│   ├── ChenAiAgentApplication.java     # 启动类（开启定时任务）
│   ├── agent/                          # Agent 框架：BaseAgent → ReActAgent → ToolCallAgent → ChenManus
│   ├── planner/                        # LLM 规划器（Plan / PlanStep）
│   ├── task/                           # 任务运行时（39 个类，见下）
│   ├── app/                            # LoveApp 恋爱专家
│   ├── rag/                            # RAG 文档加载
│   ├── chatmemory/                     # 基于文件的对话记忆
│   ├── tools/                          # 7 个内置工具与注册
│   ├── controller/                     # AI / 任务 / 产物 / 管理接口
│   ├── advisor/、config/、common/、constant/
├── src/main/resources/
│   ├── application.yml                 # 主配置
│   ├── application-local.yml           # 本地开发 profile
│   ├── application-oauth2.yml          # OAuth2 profile
│   ├── schema.sql                      # 建表与幂等迁移
│   ├── mcp-servers.json                # MCP 服务配置
│   └── document/                       # RAG 恋爱知识文档
├── chen-ai-agent-frontend/             # Vue 3 前端
├── chen-image-search-mcp-server/       # 图片搜索 MCP 子模块
├── Dockerfile / Dockerfile.frontend / docker-compose.yml
└── pom.xml
```

`task/` 包关键组件：

| 组件 | 职责 |
|---|---|
| `TaskRuntimeService` | 任务执行主流程：规划 → 调度 → 审批 → 审查 → 修复 |
| `LlmPlanner`（planner 包） | 调用大模型生成结构化计划 |
| `TaskManager` | 内存聚合 + 事件发布，条带锁 + 条件保存 |
| `TaskRepository` / `TaskAutomationRepository` | JDBC 持久化（事务、CAS） |
| `TaskQueueService` / `TaskQueueWorker` | 队列、轮询执行、死信队列 |
| `TaskApprovalService` / `TaskApprovalPolicyService` | 人工审批与风险策略 |
| `TaskReviewerService` | 结果验收 |
| `AgentTeamPlannerService` / `AgentHandoffService` / `AgentRolePromptService` | 角色分配与交接 |
| `ArtifactService` / `ArtifactAccessService` | 产物捕获与安全访问 |
| `TaskMemoryService` | 作用域语义记忆 |
| `TaskMetricsService` / `TaskQuotaService` | 指标与配额 |
| `TaskScheduleService` / `TaskTemplateService` | 模板与 Cron 调度 |
| `RedisDistributedLockService` | 分布式锁 |
| `RequestIdentityService` / `TaskScopeService` / `TaskTenantContext` | 身份解析与租户隔离 |

---

## 快速开始

### 前置要求

- JDK 21
- Node.js 22（前端）
- Maven Wrapper（仓库自带 `mvnw`，无需单独安装 Maven）
- Chroma（RAG / 语义记忆需要；Docker 一键启动时已包含）
- 通义千问 API Key（DashScope）

### 方式一：本地开发（H2 文件库，无需 Redis / PostgreSQL）

1. 启动 Chroma（向量库，监听 8000 端口）：

   ```bash
   docker run -d --name chroma -p 8000:8000 chromadb/chroma:0.5.15
   ```

2. 配置 API Key 并启动后端（默认端口 8123，上下文路径 `/api`）：

   ```bash
   # Linux / macOS
   export AI_DASHSCOPE_API_KEY=sk-xxxxxxxx
   export SEARCH_API_KEY=xxxxxxxx          # 可选，网页搜索工具
   ./mvnw spring-boot:run

   # Windows PowerShell
   $env:AI_DASHSCOPE_API_KEY="sk-xxxxxxxx"
   .\mvnw.cmd spring-boot:run
   ```

   也可使用本地开发 profile（`SPRING_PROFILES_ACTIVE=local`）。

3. 启动前端（端口 5173，`/api` 自动代理到 8123）：

   ```bash
   cd chen-ai-agent-frontend
   npm install
   npm run dev
   ```

4. 访问：

   - 前端：http://localhost:5173
   - 接口文档：http://localhost:8123/api/swagger-ui.html
   - 健康检查：http://localhost:8123/api/actuator/health

### 方式二：Docker Compose 一键启动（PostgreSQL + Redis + Chroma + 前后端）

```bash
cp .env.example .env
# 编辑 .env，填入 AI_DASHSCOPE_API_KEY，并修改 POSTGRES_PASSWORD
docker compose up -d --build
```

启动后前端：http://localhost:5173 ，后端：http://localhost:8123/api 。

数据持久化在宿主机 `./data/` 目录（postgres / redis / chroma / chat-memory / task-memory / artifacts）。

---

## 配置项

所有配置均可通过环境变量覆盖，完整列表见 `application.yml`。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AI_DASHSCOPE_API_KEY` | （必填） | 通义千问 API Key |
| `SEARCH_API_KEY` | 空 | 网页搜索工具的 API Key |
| `API_KEY` | 空 | 旧版 `/api/ai/**` 的 API Key |
| `DATABASE_URL` | `jdbc:h2:file:./data/chenmanus-db` | 数据库连接（可切 PostgreSQL） |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `sa` / 空 | 数据库账号 |
| `REDIS_URL` | `redis://127.0.0.1:6379` | Redis 地址 |
| `CHENMANUS_REDIS_ENABLED` | `false` | 是否启用 Redis 队列与分布式锁 |
| `CHENMANUS_MAX_PARALLEL_STEPS` | `4` | 单任务并行步骤上限 |
| `CHENMANUS_MAX_ACTIVE_TASKS_PER_TENANT` | `20` | 每租户活跃任务配额 |
| `CHENMANUS_APPROVAL_ENABLED` | `true` | 是否启用人工审批 |
| `CHENMANUS_APPROVAL_REQUIRED_TYPES` | `DEPLOY,PURCHASE,PAYMENT` | 强制审批的步骤类型 |
| `CHENMANUS_APPROVAL_REQUIRED_KEYWORDS` | 发布、部署、删除、购买、支付等 | 强制审批的风险关键词 |
| `CHENMANUS_SECURITY_MODE` | `legacy` | 安全模式：`legacy` / `oauth2` |
| `CHENMANUS_OIDC_ISSUER_URI` / `CHENMANUS_OIDC_AUDIENCE` | 空 | OIDC 发行方与受众 |
| `CHENMANUS_LEGACY_ADMIN_TOKEN` | 空 | legacy 模式管理接口令牌 |
| `CHENMANUS_ARTIFACT_ALLOWED_ROOT` | `./data` | 产物允许访问的根目录 |
| `CHENMANUS_INPUT_COST_PER_1K` / `CHENMANUS_OUTPUT_COST_PER_1K` | `0.0` | 每千 token 估算成本 |

MCP 服务在 `src/main/resources/mcp-servers.json` 中配置（可参考 `mcp-servers.example.json`），第三方 Key 通过环境变量注入，不要提交真实密钥。

---

## 前端页面

| 路径 | 页面 |
|---|---|
| `/` | 应用中心（Home） |
| `/love` | 恋爱心理助手 |
| `/manus` | 原版 ReAct Agent 对话 |
| `/chenmanus` | ChenManus 任务工作区（计划、实时事件、审批、产物） |

---

## HTTP API

所有接口统一前缀 `/api`，响应统一为 `ApiResponse` 结构。

### ChenManus 任务接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/tasks` | 创建任务并入队（body：`prompt`、`tenantId`、`userId`、`sessionId`、`priority`） |
| GET | `/tasks` | 按 tenant / user / session 查询任务 |
| GET | `/tasks/quota` | 查询租户配额（管理员） |
| GET | `/tasks/{taskId}` | 任务详情（含步骤、产物、审查结果） |
| POST | `/tasks/{taskId}/pause` | 暂停 |
| POST | `/tasks/{taskId}/resume` | 恢复 |
| POST | `/tasks/{taskId}/approve` | 批准待审批步骤（body：`note`） |
| POST | `/tasks/{taskId}/reject` | 驳回待审批步骤 |
| POST | `/tasks/{taskId}/cancel` | 取消 |
| GET | `/tasks/{taskId}/events` | SSE 实时事件流（连接时回放最近 200 条） |
| GET | `/tasks/{taskId}/events/history` | 审计历史，可按 `from` / `to` / `types` / `stepId` / `limit` 过滤 |
| GET | `/tasks/{taskId}/artifacts/{artifactId}/download` | 下载产物 |
| GET | `/tasks/{taskId}/artifacts/{artifactId}/preview` | 预览 PDF / 图片 / 文本 |
| GET | `/tasks/admin/dlq` | 查看死信队列（管理员） |
| POST | `/tasks/admin/dlq/replay` | 重放死信任务（管理员） |

创建任务示例：

```bash
curl -X POST http://localhost:8123/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"prompt":"调研 Spring AI 2.0 的 MCP 用法并输出一份 PDF 报告","priority":"NORMAL"}'
```

### AI / LoveApp 接口（保留）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ai/chat` | 通用对话 |
| GET | `/ai/manus/chat` | 原版 ReAct Agent 对话（SSE） |
| GET | `/ai/love/chat` | 恋爱专家对话 |
| GET | `/ai/love/chat/sse` | 恋爱专家对话（SSE 流式） |
| POST | `/ai/love/report` | 生成结构化恋爱报告 |
| GET | `/ai/love/rag` | RAG 知识库问答 |
| GET | `/ai/love/tools` | 查看内置工具 |
| GET | `/ai/love/mcp` | 查看已加载的 MCP 工具 |

---

## 内置工具

通过 `ToolRegistration` 注册 7 个工具，ChenManus 子 Agent 与 LoveApp 均可调用：

| 工具 | 能力 |
|---|---|
| `WebSearchTool` | 网页搜索（需配置 `SEARCH_API_KEY`） |
| `WebScrapingTool` | 抓取并解析网页内容（Jsoup） |
| `FileOperationTool` | 受控文件读写 |
| `ResourceDownloadTool` | 下载网络资源到本地 |
| `TerminalOperationTool` | 终端命令执行（命令白名单，安全拦截） |
| `PDFGenerationTool` | 生成 PDF（iText，支持中文字体） |
| `TerminateTool` | 主动结束当前任务循环 |

---

## MCP 外部工具

通过 Spring AI MCP Client 以声明式方式接入外部服务（`mcp-servers.json`）：

- **高德地图**（`@amap/amap-maps-mcp-server`）：地理编码、POI、路线规划等。
- **Pexels 图片搜索**（`chen-image-search-mcp-server` 子模块）：按关键词检索图片。

MCP 工具与内置工具统一注册，Agent 可在同一次推理中混合调用。

---

## 人工审批与审计

- **审批策略**：Planner 输出的 `requiresApproval` 与服务端策略合并；服务端按步骤类型（`DEPLOY/PURCHASE/PAYMENT`）与风险关键词（中英文）强制要求人工确认。
- **审批流程**：审批前步骤保持 `PENDING`、任务进入 `WAITING_USER`；批准在同一事务内将步骤置为 `APPROVED`、任务置为 `QUEUED`；驳回则置为 `REJECTED`、任务置为 `CANCELLED`。
- **事务语义**：批准后的重新入队在事务提交后（`afterCompletion`）执行；事务回滚不入队，且内存状态自动从数据库重载。
- **并发防护**：审批状态转移使用条件更新（CAS），仅当数据库中任务仍为 `WAITING_USER` 时生效；并发双审或审批 / 取消交错时，后到方失败回滚（HTTP 409）。
- **审计历史**：全部 26 类事件强制持久化，写入失败即回滚；审批事件 message 含 `actor=<user>`；过滤条件在数据库侧先于 `LIMIT` 执行；事件按 `created_at + seq` 双键排序，同毫秒保持插入顺序；审计事件外键为 `RESTRICT`，删除任务不会抹掉审计轨迹。

---

## 安全与多租户

- **legacy 模式（默认）**：接口整体放行；管理接口（`/tasks/admin/**`、`/tasks/quota`）默认关闭，需配置 `CHENMANUS_LEGACY_ADMIN_TOKEN` 后通过 `X-Admin-Token` 访问（常量时间比较）；未验证身份的审批 actor 标记为 `legacy:<user>`，不会冒充已认证主体。
- **oauth2 模式**（`CHENMANUS_SECURITY_MODE=oauth2`）：校验 OIDC JWT，`sub`/`user_id` 提供用户身份，`tenant_id`/`tenant` 提供租户身份（缺失则拒绝），`roles`/`realm_access.roles`/`permissions` 映射为角色。
  - `TENANT_ADMIN`：管理本租户任务、执行审批；
  - `PLATFORM_ADMIN`：跨租户访问与管理；
  - 普通用户：只能访问自己的任务。
- **可信网关模式**：`CHENMANUS_TRUST_IDENTITY_HEADERS` / `CHENMANUS_REQUIRE_IDENTITY_HEADERS` 控制是否信任网关注入的身份头。
- **产物安全**：Artifact 接口只允许读取 `CHENMANUS_ARTIFACT_ALLOWED_ROOT` 下的真实文件（校验 real path），禁止外部 URL、路径穿越与软链接逃逸。

---

## CI

GitHub Actions（`.github/workflows/ci.yml`）在 `master` 与 `feature/**` 分支推送、以及针对 `master` 的 PR 上运行：

- **后端**：`./mvnw -q -DskipTests package` 编译打包；离线单元测试覆盖持久化、配额、队列 / DLQ、指标、产物、角色路由、身份与租户隔离、审批策略、任务管理、分布式锁等。
- **前端**：`npm ci && npm run build`。

依赖真实大模型 / 第三方 API 的集成测试不作为默认离线门槛。

---

## 兼容性说明

- 旧版 `/api/ai/**` 接口与 `/manus` 页面继续保留，新版 `/api/tasks` 与 `/chenmanus` 提供任务化运行时。
- `schema.sql` 使用 `CREATE TABLE IF NOT EXISTS` 与幂等 `ALTER TABLE`，老库可平滑升级。

---

## License

[MIT](LICENSE)
