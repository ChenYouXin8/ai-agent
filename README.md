# chen-ai-agent

**ChenManus 2.8**：基于 Spring Boot + Spring AI 的通用 Agent 运行时平台。

当前能力：LLM Planner、DAG `dependsOn`、并行子 Agent、Team Planner、Agent Handoff、Reviewer/Repair、Redis Queue、Distributed Lock、DLQ、PostgreSQL/H2、租户隔离、OAuth2/OIDC JWT、RBAC、任务/步骤 Usage、Artifact 安全下载/预览，以及 `/chenmanus` Workspace。

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

网关注入 `X-Tenant-Id` / `X-User-Id` 后，服务端使用可信身份，不依赖客户端 body/query 中的租户归属。

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tasks` | 创建任务并入队 |
| GET | `/api/tasks` | 按 tenant/user/session 查询 |
| GET | `/api/tasks/quota` | 租户任务配额 |
| GET | `/api/tasks/{taskId}` | 任务详情 |
| POST | `/api/tasks/{taskId}/pause` | 暂停 |
| POST | `/api/tasks/{taskId}/resume` | 恢复 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时事件 |
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
LLM Planner → Execution DAG
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
