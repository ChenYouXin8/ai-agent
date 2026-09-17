# chen-ai-agent

**ChenManus 2.9**：基于 Spring Boot + Spring AI 的通用 Agent 运行时平台。

当前能力：LLM Planner、DAG `dependsOn`、并行子 Agent、Team Planner、Agent Handoff、Reviewer/Repair、Redis Queue、Distributed Lock、DLQ、PostgreSQL/H2、租户隔离、OAuth2/OIDC JWT、RBAC、任务/步骤 Usage、Artifact 安全下载/预览、Human Approval、Approval Policy、持久化 Event Audit，以及 `/chenmanus` Workspace。

## 2.9 Human Approval / Audit

Planner 的 `requiresApproval` 会与服务端 Approval Policy 合并判断：服务端可按步骤类型和风险关键词强制要求人工确认。审批前步骤保持 `PENDING`，任务进入 `WAITING_USER`；批准会在同一事务内将步骤置为 `APPROVED`、任务置为 `QUEUED`，并记录 `TASK_APPROVAL_GRANTED` 与 `TASK_QUEUED`；驳回会将步骤置为 `REJECTED`、任务置为 `CANCELLED`，并记录对应审批/取消事件。

事务语义：批准产生的重新入队发生在事务**提交之后**（`afterCompletion` 回调），事务回滚时不会入队，且内存中的任务状态会自动从数据库恢复，避免 worker 消费回滚后的脏状态。

并发防护：审批状态转移使用条件更新（CAS）写入——仅当数据库中任务仍为 `WAITING_USER` 时生效。并发双审、审批与驳回/取消交错时，后到的一方会因状态已变更而失败回滚（HTTP 409），不会产生重复审批事件、重复入队或"已批准步骤 + 已取消任务"的矛盾状态。任务聚合的保存以单数据库事务写入（任务行、步骤、产物原子提交），同一实例内按任务串行化，多实例部署时由任务行锁在数据库层串行化并发保存，步骤不会被交错覆盖或重复插入。

审批权限：`CHENMANUS_SECURITY_MODE=oauth2` 时仅 `TENANT_ADMIN` / `PLATFORM_ADMIN` 可审批；legacy 模式整体无鉴权（permitAll），审批接口同样放行，但审计事件中的 actor 会标记为 `legacy:<user>`（未验证身份），不会冒充已认证主体。管理接口（`/api/tasks/admin/**` 与 `/api/tasks/quota`）在 oauth2 模式下要求管理员角色；legacy 模式下默认关闭，需配置 `CHENMANUS_LEGACY_ADMIN_TOKEN` 后凭请求头 `X-Admin-Token` 访问（常量时间比较）。

分布式锁：`CHENMANUS_REDIS_ENABLED=true` 时任务执行与调度加分布式锁。锁获取 fail-closed——Redis 不可用时任务留在队列中等待重试，而不是在无锁状态下并发执行；解锁通过 Lua CAS 脚本原子完成（仅当锁仍属于当前持有者才删除），TTL 作为 Redis 故障期间的最终安全网。

审计历史：

`GET /api/tasks/{taskId}/events/history?limit=200&from=...&to=...&types=TASK_APPROVAL_GRANTED,TASK_CANCELLED&stepId=...`

返回持久化事件，审批事件 message 会包含 `actor=<user>`，SSE 与历史事件保持同一事件语义。所有任务事件（含 `TASK_APPROVAL_REQUIRED` 与步骤/工具事件）均强制持久化：写入失败会使当次操作失败并回滚，不会静默丢失。历史查询的 `from`/`to`/`types`/`stepId` 过滤在数据库侧先于条数限制执行，任意时间段的事件均可检索（单次最多返回 `limit` 条，上限 500）。事件按 `created_at` 与自增 `seq` 双键排序，同毫秒事件保持插入顺序；审计事件不随任务删除而级联清除（外键 RESTRICT），删除任务需先显式清理事件。

可通过以下配置调整策略：

```bash
CHENMANUS_APPROVAL_ENABLED=true
CHENMANUS_APPROVAL_REQUIRED_TYPES=DEPLOY,PURCHASE,PAYMENT
CHENMANUS_APPROVAL_REQUIRED_KEYWORDS=deploy,publish,send,delete,purchase,pay,production,发布,部署,上线,发送,删除,购买,支付,生产
```

## 安全与多租户

OAuth2 模式：

```bash
CHENMANUS_SECURITY_MODE=oauth2
CHENMANUS_OIDC_ISSUER_URI=https://idp.example.com/realms/chenmanus
CHENMANUS_OIDC_AUDIENCE=https://api.example.com
```

JWT 的 `sub`/`user_id` 提供用户身份，`tenant_id`/`tenant` 提供租户身份；缺少租户 claim 会拒绝请求。`roles`、`realm_access.roles`、`permissions` 映射为 Spring Security roles。

`TENANT_ADMIN` 可以管理本租户任务，`PLATFORM_ADMIN` 可以跨租户访问任务；普通用户只能访问自己的任务。配置 `CHENMANUS_OIDC_AUDIENCE` 后启用 `aud` 校验。

Legacy 可信网关模式：

```bash
CHENMANUS_TRUST_IDENTITY_HEADERS=true
CHENMANUS_REQUIRE_IDENTITY_HEADERS=true
```

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tasks` | 创建任务并入队 |
| GET | `/api/tasks` | 按 tenant/user/session 查询 |
| GET | `/api/tasks/quota` | 租户任务配额 |
| GET | `/api/tasks/{taskId}` | 任务详情 |
| POST | `/api/tasks/{taskId}/pause` | 暂停 |
| POST | `/api/tasks/{taskId}/resume` | 恢复 |
| POST | `/api/tasks/{taskId}/approve` | 人工批准待审批步骤 |
| POST | `/api/tasks/{taskId}/reject` | 人工驳回待审批步骤 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时事件 |
| GET | `/api/tasks/{taskId}/events/history` | 审计历史，可按时间/类型/步骤过滤 |
| GET | `/api/tasks/{taskId}/artifacts/{artifactId}/download` | Artifact 下载 |
| GET | `/api/tasks/{taskId}/artifacts/{artifactId}/preview` | PDF/图片/文本预览 |
| GET | `/api/tasks/admin/dlq` | 管理员查看 DLQ |
| POST | `/api/tasks/admin/dlq/replay` | 管理员重放 DLQ |

## Runtime

```text
User / OIDC / Auth Gateway
        ↓
JWT → Tenant + User + Roles
        ↓
TaskController
        ↓
PostgreSQL / H2
        ↓
Redis Queue → Worker → Distributed Lock
        ↓
LLM Planner → Approval Policy → Execution DAG
        ↓
Team Planner → Agent Handoff → ChenManus child agents
        ↓
Artifact + Metrics
        ↓
Reviewer → Repair / PASS
        ↓
Tenant-scoped Memory
        ↓
Final Result

Failure → DLQ → Admin Replay
```

## Artifact 安全

Artifact API 只允许读取 `CHENMANUS_ARTIFACT_ALLOWED_ROOT` 下的真实文件，并检查 real path；禁止 HTTP/HTTPS 外部地址和路径穿越/软链接逃逸。

## Docker Compose

```bash
cp .env.example .env
docker compose up -d --build
```

服务包含 PostgreSQL、Redis、Chroma、Spring Boot backend 和 Vue frontend。

## CI

GitHub Actions 执行 backend compile/package、离线单元测试和 frontend build。离线测试覆盖持久化、配额、队列/DLQ、指标、Artifact、角色路由、身份及租户隔离；live-model/第三方 API 集成测试不作为默认离线门槛。

## 兼容性

原 `/api/ai/*` 与 `/manus` 页面继续保留；新的 `/api/tasks` 与 `/chenmanus` 提供任务化运行时。
