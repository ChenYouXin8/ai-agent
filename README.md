# chen-ai-agent

基于 **Spring Boot + Spring AI** 的智能对话 Agent 项目。集成通义千问大模型、Chroma 向量数据库与文件持久化记忆，提供通用对话、恋爱心理顾问（RAG 知识库增强）与结构化报告能力。

## ✨ 功能特性

- **通用对话**：基于通义千问 `qwen-max` 的对话接口
- **恋爱心理顾问**：内置系统提示词，按「单身 / 恋爱 / 已婚」三种状态引导用户描述问题
- **RAG 知识库问答**：从 Chroma 向量库检索恋爱知识文档，结合检索内容回答，提升专业性与准确性
- **多轮对话记忆**：基于 Kryo 序列化的文件持久化记忆，按 `chatId` 隔离不同会话
- **结构化输出**：调用模型按 `LoveReport` 结构返回「标题 + 建议列表」
- **接口文档**：集成 Knife4j / Swagger UI，可视化调试所有接口

## 🛠 技术栈

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 | AI 应用框架 |
| Java | 21 | 运行环境 |
| 通义千问 DashScope | qwen-max / text-embedding-v3 | 对话与 Embedding 模型 |
| Chroma | 本地持久化（数据存 `.chroma/`） | 向量数据库 |
| Kryo | 5.6.2 | 对话记忆序列化 |
| Knife4j | 4.4.0 | 接口文档 |

> 模型通过 DashScope **兼容 OpenAI 协议**的端点接入（`base-url: https://dashscope.aliyuncs.com/compatible-mode/v1`），因此使用 `spring-ai-starter-model-openai` 即可对接通义千问。

## 📁 项目结构

```
chen-ai-agent
├── src/main/java/io/github/chenyouxin8/chenaiagent
│   ├── ChenAiAgentApplication.java        # 启动类
│   ├── app
│   │   └── LoveApp.java                   # 恋爱专家核心（三种能力）
│   ├── chatmemory
│   │   └── FileBasedChatMemory.java        # Kryo 文件持久化对话记忆
│   ├── config
│   │   └── VectorStoreConfig.java          # Chroma 向量存储初始化（分批写入）
│   ├── controller
│   │   ├── AiController.java               # 通用对话接口
│   │   └── LoveAppController.java          # 恋爱专家接口
│   └── rag
│       └── LoveAppDocumentLoader.java      # 加载 document/*.md 知识库
├── src/main/resources
│   ├── application.yml                     # 配置（DashScope + Chroma）
│   └── document/                           # 知识库 Markdown（3 篇，运行时切分为 15 个文档）
│       ├── 恋爱常见问题和回答 - 单身篇.md
│       ├── 恋爱常见问题和回答 - 已婚篇.md
│       └── 恋爱常见问题和回答 - 恋爱篇.md
└── pom.xml
```

## 🚀 快速开始

### 环境要求

- JDK 21
- Python 3.11+（用于运行 Chroma 向量库）
- 通义千问 API Key（[DashScope](https://dashscope.console.aliyun.com/) 申请）

### 1. 启动 Chroma 向量数据库

```bash
pip install chromadb
chroma run --host 0.0.0.0 --port 8000
```

保持该进程运行。向量数据持久化在当前目录的 `.chroma/` 文件夹，重启不丢失。

### 2. 配置 API Key

设置环境变量（推荐）：

```bash
# Linux / macOS
export AI_DASHSCOPE_API_KEY=你的_DashScope_API_Key

# Windows (PowerShell)
$env:AI_DASHSCOPE_API_KEY="你的_DashScope_API_Key"
```

或在 `application.yml` 中把 `${AI_DASHSCOPE_API_KEY}` 替换为真实 Key。

### 3. 启动项目

```bash
./mvnw spring-boot:run
# 或使用项目自带脚本
./start-local.cmd
```

启动日志出现 `Tomcat started on port 8123` 即成功，同时会自动把知识库文档写入 Chroma 向量存储。

### 4. 访问接口文档

打开 **http://localhost:8123/api/swagger-ui.html** ，即可在线调试所有接口。

## 📡 接口说明

所有接口统一带 `context-path=/api`，服务端口 `8123`。

| 方法 | 路径 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/api/ai/chat` | 通用对话 | `message` |
| GET | `/api/ai/love/chat` | 恋爱专家（多轮记忆） | `message`, `chatId` |
| POST | `/api/ai/love/report` | 结构化恋爱报告 | Body: `{ "message": "...", "chatId": "..." }` |
| GET | `/api/ai/love/rag` | 知识库 RAG 问答 | `message`, `chatId` |

### 调用示例

```bash
# 通用对话
curl "http://localhost:8123/api/ai/chat?message=你好"

# 恋爱专家（多轮对话，chatId 区分会话）
curl "http://localhost:8123/api/ai/love/chat?message=我是单身，该怎么扩大社交圈&chatId=user-001"

# 知识库 RAG 问答
curl "http://localhost:8123/api/ai/love/rag?message=异地恋该怎么维持&chatId=user-001"

# 结构化报告
curl -X POST "http://localhost:8123/api/ai/love/report" \
  -H "Content-Type: application/json" \
  -d '{"message":"我总是追不到喜欢的人","chatId":"user-001"}'
```

## 🧠 知识库与 RAG

- 知识库文档放在 `src/main/resources/document/`，支持 `.md` 文件，启动时自动加载。
- `LoveAppDocumentLoader` 用 `MarkdownDocumentReader` 按标题 / 水平线将每篇文档切分为多个片段（3 篇文档共切出 15 个片段）。
- `VectorStoreConfig` 在应用启动时将文档写入 Chroma 向量库；受 DashScope `text-embedding-v3` **单次批量上限 10 条**限制，代码已做分批写入。
- RAG 接口 `/api/ai/love/rag` 通过 `RetrievalAugmentationAdvisor` + `VectorStoreDocumentRetriever` 先检索相关片段，再交给模型回答。

### 扩展知识库

往 `src/main/resources/document/` 放入新的 `.md` 文件，重启项目即可自动入库（Chroma 已配置 `initialize-schema: true`）。

## 💾 对话记忆持久化

- 记忆由 `FileBasedChatMemory` 实现，使用 Kryo 将对话序列化为 `.kryo` 文件，存于项目根目录的 `.chat-memory/` 文件夹。
- 按 `chatId` 隔离：每个会话对应一个 `{chatId}.kryo` 文件。
- 默认携带最近 10 条上下文（`chat_memory_retrieve_size=10`）。
- 清除某会话记忆：调用 `FileBasedChatMemory.clear(chatId)`。

## ⚙️ 关键配置（application.yml）

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max          # 对话模型
      embedding:
        options:
          model: text-embedding-v3 # 向量化模型
    vectorstore:
      chroma:
        collection-name: love-app-knowledge
        initialize-schema: true
        client:
          host: http://127.0.0.1
          port: 8000               # 需与 Chroma 服务端口一致
server:
  port: 8123
  servlet:
    context-path: /api
```

## 🗺 后续扩展方向

- 接入微信 / 网页前端，把 `LoveApp` 暴露为对话服务
- 扩充知识库到更多领域（如情感沟通话术、心理疏导等）
- 数据量增大后，将向量库从 Chroma 迁移到 PGVector / Milvus
- 引入 Rerank 提升检索精度

## 📄 许可证

[MIT](LICENSE) © 2026 ChenYouXin8
