# chen-ai-agent

**基于 Spring Boot + Spring AI 的恋爱心理 AI 助手**，集成通义千问大模型、Chroma 向量数据库、RAG 知识库问答、AI 工具调用与 MCP 外部服务扩展能力。

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0-green.svg)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 功能特性

- **恋爱心理专家**：内置系统提示词，按单身/恋爱/已婚三阶段引导用户描述问题
- **RAG 知识库问答**：从 Chroma 向量库检索恋爱文档，结合上下文提升回答专业性
- **结构化报告**：模型按 LoveReport 格式输出标题 + 建议列表
- **多轮对话记忆**：Kryo 文件持久化，按 chatId 隔离会话上下文
- **内置 7 个 AI 工具**：网页搜索、网页抓取、文件操作、资源下载、终端命令、PDF生成、终止会话
- **YuManus 智能体**：ReAct 模式自主拆解复杂任务，循环调用工具链式完成
- **MCP 外部扩展**：通过 mcp-servers.json 声明式接入高德地图、图片搜索等外部服务
- **统一响应 + 全局异常处理**：所有接口返回 ApiResponse，四层异常兜底
- **接口鉴权**：Bearer Token 机制，生产环境可配置开启

## 环境要求

- JDK 21
- Python 3.11+（运行 Chroma 向量库）
- [通义千问 API Key](https://dashscope.console.aliyun.com/)（必需）
- searchapi.io API Key（可选，网页搜索工具用）

## 快速开始

### 方式一：本地开发

```bash
# 1. 启动 Chroma 向量数据库
pip install chromadb
chroma run --host 127.0.0.1 --port 8000

# 2. 克隆项目
git clone https://github.com/ChenYouXin8/ai-agent.git
cd ai-agent

# 3. 配置 API Key
cp .env.example .env
# 编辑 .env，填入 AI_DASHSCOPE_API_KEY

# 4. 启动
./mvnw spring-boot:run -Dspring.profiles.active=local
```

访问 http://localhost:8123/api/swagger-ui.html 查看接口文档。

### 方式二：Docker 一键部署（推荐）

**一条命令启动全部服务**（后端 + Chroma + 前端）：

```bash
# 1. 克隆项目
git clone https://github.com/ChenYouXin8/ai-agent.git
cd ai-agent

# 2. 配置 API Key
cp .env.example .env
# 编辑 .env，填入 AI_DASHSCOPE_API_KEY

# 3. 一键启动（后端 8123 + Chroma 8000 + 前端 5173）
docker compose up -d

# 4. 访问
#   前端：http://localhost:5173
#   后端 API：http://localhost:8123/api
#   Swagger：http://localhost:8123/api/swagger-ui.html
#   健康检查：http://localhost:8123/api/actuator/health

# 停止
docker compose down

# 重新构建（代码变更后）
docker compose up -d --build
```

**Docker 部署目录结构**：
```
chen-ai-agent/
├── Dockerfile              # 后端 Spring Boot 镜像
├── Dockerfile.frontend     # 前端 Vue/Nginx 镜像
├── docker-compose.yml      # 编排：chroma + backend + frontend
├── .env.example           # 环境变量模板（复制为 .env 填入密钥）
└── data/                  # 持久化数据（.gitignore，不提交）
    ├── chroma/            #   Chroma 向量数据库文件
    └── chat-memory/       #   Kryo 对话记忆文件
```

**注意事项**：
- `AI_DASHSCOPE_API_KEY` **必须填写**，否则后端启动失败
- `API_KEY` 未设置时为开发模式（跳过 Bearer Token 鉴权）
- 前端默认代理 `/api/` 到后端，如需改端口可编辑 `docker-compose.yml`

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/ai/chat | 通用对话 |
| GET | /api/ai/manus/chat | YuManus 智能体（SSE 流式）|
| GET | /api/ai/love/chat | 恋爱专家对话 |
| POST | /api/ai/love/report | 结构化恋爱报告 |
| GET | /api/ai/love/rag | RAG 知识库问答 |
| GET | /api/ai/love/tools | 恋爱专家 + 7 个内置工具 |
| GET | /api/ai/love/mcp | 恋爱专家 + MCP 外部工具 |

```bash
# 通用对话
curl "http://localhost:8123/api/ai/chat?message=你好"

# 恋爱专家
curl "http://localhost:8123/api/ai/love/chat?message=我是单身该怎么扩大社交圈&chatId=user-001"

# RAG 知识库问答
curl "http://localhost:8123/api/ai/love/rag?message=异地恋怎么维持&chatId=user-001"

# 结构化报告
curl -X POST "http://localhost:8123/api/ai/love/report" \
  -H "Content-Type: application/json" \
  -d '{"message":"我总是追不到喜欢的女生","chatId":"user-001"}'

# YuManus SSE 流式
curl -s -N "http://localhost:8123/api/ai/manus/chat?message=查一下济南今天天气"
```

## 配置说明

### 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| AI_DASHSCOPE_API_KEY | 通义千问 API Key | 是 |
| SEARCH_API_KEY | searchapi.io Key（网页搜索用）| 可选 |
| API_KEY | Bearer Token 鉴权（生产环境开启）| 可选 |
| AMAP_MAPS_API_KEY | 高德地图 Key（MCP 用）| 可选 |
| PEXELS_API_KEY | Pexels Key（图片搜索 MCP 用）| 可选 |

### application.yml 关键配置

```yaml
spring:
  ai:
    openai:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat.options.model: qwen-max
      embedding.options.model: text-embedding-v3
    vectorstore:
      chroma:
        collection-name: love-app-knowledge
        client.host: http://127.0.0.1
        client.port: 8000
    mcp:
      client:
        enabled: true
        servers-configuration: classpath:mcp-servers.json
  security:
    api-key: ${API_KEY}   # 未设置则跳过鉴权
server:
  port: 8123
  servlet.context-path: /api
```

## 内置工具集

| 工具 | 说明 |
|------|------|
| WebSearchTool | 百度搜索（searchapi.io）|
| WebScrapingTool | 网页 HTML 抓取（jsoup）|
| FileOperationTool | 文件读写/复制/删除/列表 |
| ResourceDownloadTool | 下载网络资源到本地 |
| TerminalOperationTool | 白名单终端命令（安全版，禁止 Shell 连接符）|
| PDFGenerationTool | 生成 PDF（内置中文字体）|
| TerminateTool | 终止会话（Agent 自动调用）|

## Agent 架构

```
BaseAgent（状态机 + N 步执行循环）
└── ReActAgent（think/act 两阶段）
    └── ToolCallAgent（工具集注入）
        └── ChenManus（YuManus 全能助手）
```

## 项目结构

```
chen-ai-agent/
├── chen-image-search-mcp-server/    # MCP 图片搜索子模块
├── src/main/java/.../chenaiagent/
│   ├── app/LoveApp.java             # 恋爱专家核心
│   ├── agent/                       # Agent 智能体框架
│   ├── tools/                       # 7 个内置工具
│   ├── controller/                  # REST 接口
│   ├── common/                       # ApiResponse / 异常处理
│   ├── config/                       # Cors / Security / VectorStore
│   └── chatmemory/                   # Kryo 文件持久化记忆
├── src/main/resources/
│   ├── application.yml               # 公共配置（环境变量占位）
│   ├── application-local.yml         # 本地配置（含真实 Key，.gitignore）
│   ├── mcp-servers.json             # MCP 配置（含密钥，.gitignore）
│   └── document/                     # RAG 知识库文档
├── chen-ai-agent-frontend/          # Vue 3 前端
├── Dockerfile                        # 后端 Docker 镜像
├── Dockerfile.frontend               # 前端 Docker 镜像
└── docker-compose.yml               # 一键部署编排
```

## 扩展思路

**简单（1-2 天）**
- 将 ChenManus 暴露为 HTTP 接口对外服务
- 接入微信/网页前端（chen-ai-agent-frontend 已完成前端部分）
- 扩充知识库文档到更多领域（职场/家庭等）

**中等（3-7 天）**
- 引入 Rerank 提升 RAG 检索精度
- 从 Chroma 迁移到 PGVector / Milvus（支持更大规模数据）
- 在 mcp-servers.json 声明更多外部 MCP Server（零代码扩展工具集）

**有挑战（1-2 周+）**
- 多 Agent 协作链（规划/执行/审核 Agent 分工）
- 模型路由（简单问答用 qwen-plus，复杂推理用 qwen-max）
- Agent 自我学习（成功经验写回知识库）

## 许可证

MIT License
