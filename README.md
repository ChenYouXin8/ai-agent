# chen-ai-agent

**基于 Spring Boot + Spring AI 的恋爱心理 AI 助手与通用 Agent 实验平台**，集成通义千问大模型、Chroma 向量数据库、RAG 知识库问答、AI 工具调用与 MCP 外部服务扩展能力。

## ChenManus 2.0 / 2.2

ChenManus 2.0 在保留原 `/manus` Agent 能力的同时，增加了独立的任务运行时：

- **LLM Planner**：根据用户目标动态生成 2~6 个可执行步骤，并通过 Spring AI structured output 校验结构。
- **Step Runtime**：按计划逐步执行，每个步骤都携带前序结果上下文。
- **自动重试**：单个步骤失败最多自动重试 2 次。
- **Reviewer**：任务完成后由独立 Reviewer 检查结果；审核未通过时自动创建一次“补救与修正”步骤并二次审核。
- **Task Memory**：任务结果落盘到 `data/task-memory`，后续规划可参考最近任务。
- **Artifact**：自动识别 Agent 输出中的 PDF、DOCX、XLSX、图片、压缩包等交付路径，并在任务中展示。
- **真实工具事件**：通过 `ToolCallback` wrapper 捕获工具 started/completed/failed 事件，并通过 SSE 实时推送到前端。
- **Task SSE**：实时推送计划、步骤、重试、工具、审核、产物、完成/失败等事件。
- **Workspace**：新增 `/chenmanus` 工作区，提供任务历史、计划、Reviewer、交付产物和事件时间线。

## 环境要求

- JDK 21
- Python 3.11+（运行 Chroma 向量库）
- [通义千问 API Key](https://dashscope.console.aliyun.com/)（必需）
- searchapi.io API Key（可选，网页搜索工具用）

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
```

前端位于 `chen-ai-agent-frontend/`，开发时执行：

```bash
npm install
npm run dev
```

然后打开 `/chenmanus` 进入 ChenManus 2.0 Workspace。

## ChenManus 任务 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tasks` | 创建并异步启动任务 |
| GET | `/api/tasks` | 获取最近任务 |
| GET | `/api/tasks/{taskId}` | 获取任务详情、步骤、Reviewer、产物 |
| POST | `/api/tasks/{taskId}/pause` | 暂停任务 |
| POST | `/api/tasks/{taskId}/resume` | 恢复任务 |
| POST | `/api/tasks/{taskId}/cancel` | 取消任务 |
| GET | `/api/tasks/{taskId}/events` | SSE 实时任务事件 |

创建任务：

```bash
curl -X POST http://localhost:8123/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"prompt":"研究 AI Agent 最近的发展并整理成报告"}'
```

监听事件：

```bash
curl -N http://localhost:8123/api/tasks/task_xxxxxxxxxxxx/events
```

## ChenManus 架构

```text
User
  ↓
TaskController
  ↓
TaskManager
  ↓
LLM Planner ─────→ TaskPlan
  ↓
TaskRuntime
  ├── Step 1
  ├── Step 2
  ├── Step 3 ...
  │     ↓
  │   ChenManus
  │     ↓
  │   ToolCallback / MCP / RAG / Files
  │     ↓
  │   Tool Events
  ↓
Reviewer
  ├── PASS
  └── REPAIR → re-review
  ↓
Artifacts + Task Memory
  ↓
Final Result
```

## 保持兼容

旧接口和旧 `/manus` 页面继续保留；ChenManus 2.0 通过新 `/tasks` API 和 `/chenmanus` Workspace 提供任务化运行时。

## CI

GitHub Actions 会同时检查后端 Maven 编译/离线单测和前端 npm 构建。运行时依赖外部 API Key 的集成测试不作为默认 CI 门槛。
