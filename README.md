# chen-ai-agent

**基于 Spring Boot + Spring AI 的恋爱心理 AI 助手与通用 Agent 运行时平台**。

ChenManus 2.x 在保留原 `/manus` 能力的同时，增加任务规划、逐步执行、Reviewer、长期记忆、Artifact、并行子 Agent 与角色化执行。

## ChenManus 2.4

- **LLM Planner**：使用 Spring AI structured output 动态生成 2~6 个执行步骤。
- **Step Runtime**：每个步骤由独立 ChenManus 执行，自动带上前序结果。
- **自动重试**：单步失败最多重试 2 次。
- **Reviewer + Repair**：结果由独立 Reviewer 审核；不通过时自动生成一次补救步骤并二次审核。
- **并行子 Agent**：Planner 可标记 `parallelizable=true`，彼此独立的相邻步骤会并发启动多个 ChenManus。
- **角色化 Agent**：根据步骤类型注入 Researcher / Analyst / Coder / Writer / General 角色指令。
- **语义长期记忆**：任务记忆写入现有 Spring AI VectorStore，并按 `ownerId + sessionId` 过滤；VectorStore 不可用时回退到文件检索。
- **任务持久化**：JDBC + H2 保存任务、步骤、Reviewer、Artifact、用户与会话信息。
- **真实工具事件**：通过 `ToolCallback` wrapper 捕获 TOOL_STARTED / TOOL_COMPLETED / TOOL_FAILED。
- **Artifact**：自动识别 PDF、DOCX、XLSX、CSV、图片、ZIP、TXT 等交付路径。
- **Workspace**：`/chenmanus` 展示任务计划、并行步骤、Reviewer、Artifacts 与实时事件。

## 环境要求

- JDK 21
- Python 3.11+（Chroma）
- 通义千问 API Key
- searchapi.io API Key（可选）

## 快速开始

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

## 任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tasks` | 创建并异步执行任务 |
| GET | `/api/tasks?userId=&sessionId=` | 按用户/会话查询任务 |
| GET | `/api/tasks/{taskId}` | 获取任务详情 |
| POST | `/api/tasks/{taskId}/pause` | 暂停 |
| POST | `/api/tasks/{taskId}/resume` | 恢复 |
| POST | `/api/tasks/{taskId}/cancel` | 取消 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时事件 |

示例：

```bash
curl -X POST http://localhost:8123/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"prompt":"研究 AI Agent 最近的发展，分别收集多个来源，最后整理成报告","userId":"demo-user","sessionId":"demo-session"}'
```

## Runtime 架构

```text
User
 ↓
TaskController
 ↓
TaskManager
 ├── H2 / JDBC persistence
 └── userId + sessionId isolation
 ↓
LLM Planner ←──── semantic Task Memory
 ↓
Plan / PlanStep
 ↓
┌────────────────────────────────────┐
│ sequential / parallel child agents │
│                                    │
│ Researcher → ChenManus → Tools     │
│ Researcher → ChenManus → Tools     │  ← parallel
│ Analyst    → ChenManus → Tools     │
│ Coder      → ChenManus → Files     │
└────────────────────────────────────┘
 ↓
ArtifactService
 ↓
TaskReviewerService
 ├── PASS
 └── REPAIR → child agent → re-review
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

`chen_task_steps.parallelizable` 用于持久化并行执行元数据。

## CI

GitHub Actions 执行后端 Maven compile/package、离线单元测试和前端 npm build。依赖真实模型、第三方 API 或外网服务的集成测试不作为默认离线 CI 门槛。

## 兼容性

旧 `/api/ai/*` 接口和 `/manus` Classic 页面继续保留；ChenManus 2.x 通过新的 `/api/tasks` 与 `/chenmanus` Workspace 提供任务化运行时。

## 下一层

- PostgreSQL + Redis + 分布式任务队列
- 完整 DAG 依赖图与并发配额
- 向量记忆压缩、摘要与长期画像
- Artifact 下载/预览/版本管理
- token、成本、耗时和任务质量指标

## 许可证

MIT License
