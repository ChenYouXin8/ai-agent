# 🤖 Chen Ai Agent

> **基于 Spring AI + 通义千问的多场景 AI 对话服务，支持多轮记忆持久化与结构化输出。**

Chen Ai Agent 是一个由 Spring Boot 驱动的 AI 对话平台，通过 [Spring AI](https://spring.io/projects/spring-ai) 框架接入**阿里云通义千问（Qwen-Max）**模型。支持通用对话、场景化 Agent（恋爱顾问）、多轮记忆持久化（文件存储）、结构化输出，并提供自动生成的交互式 API 文档。

[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen?style=flat-square&logo=spring)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-blue?style=flat-square&logo=spring)](https://github.com/spring-projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

---

## ✨ 核心特性

### 🧠 通用 AI 对话

通过 `/api/ai/chat` 接口，与通义千问旗舰模型（Qwen-Max）进行自由对话，无需额外配置，开箱即用。

### 💕 恋爱顾问 Agent

内置 `LoveApp` 场景化对话 Agent，扮演深耕恋爱心理领域的专家，支持**单身、恋爱、已婚**三种状态的情感咨询。引入对话记忆（ChatMemory），支持多轮上下文连贯对话。

### 💾 文件持久化对话记忆

`FileBasedChatMemory` 实现基于 Kryo 序列化的文件存储，对话历史可跨会话、跨实例持久保存，支持按需检索最近 N 条消息。

### 📋 结构化输出

`LoveApp.doChatWithReport()` 演示了 Spring AI 的结构化输出能力，将 AI 回复自动解析为 `LoveReport` Java Record，便于后续业务处理。

### 📖 自动 API 文档

集成 **Knife4j** + **SpringDoc OpenAPI**，服务启动后自动生成中文交互式 API 文档，浏览器直接调试所有接口。

### 🧪 完整单元测试

针对核心 Agent 逻辑（`LoveApp`）和对话记忆实现（`FileBasedChatMemory`）编写了完整的 JUnit 5 + Mockito 单元测试。

---

## 🚀 快速开始

### 环境要求

- JDK 21+
- Windows（启动脚本为 `.cmd`，Linux/Mac 可用 `mvnw`）

### 安装 & 启动

**方式一：一键启动（Windows）**

```cmd
双击运行 start-local.cmd
```

> `start-local.cmd` 默认读取 `D:\projectt\chen-ai-agent` 目录。如目录不同，请修改脚本内路径后运行。

**方式二：命令行启动**

```bash
# 克隆仓库
git clone https://github.com/ChenYouXin8/ai-agent.git
cd ai-agent

# 设置 API Key
set AI_DASHSCOPE_API_KEY=your_api_key_here

# Windows 启动
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"

# Linux/Mac 启动
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### 验证服务

服务启动后，访问以下地址：

| 地址 | 说明 |
|------|------|
| http://localhost:8123/api/ai/chat?message=你好 | 通用对话接口 |
| http://localhost:8123/api/swagger-ui.html | Knife4j API 文档（中文） |
| http://localhost:8123/api/v3/api-docs | OpenAPI JSON |

---

## ⚙️ 配置说明

### 环境变量

| 变量名 | 必填 | 说明 |
|--------|:----:|------|
| `AI_DASHSCOPE_API_KEY` | ✅ | 阿里云 DashScope API Key。[获取地址](https://dashscope.console.aliyun.com) |

### application.yml 关键配置

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max   # 通义千问旗舰模型

server:
  port: 8123
  servlet:
    context-path: /api     # 所有接口统一前缀
```

> **提示**：如需切换模型，可将 `qwen-max` 改为 `qwen-plus`、`qwen-turbo` 等，模型列表见 [通义千问文档](https://help.aliyun.com/zh/dashscope)。

---

## 🗂️ 项目结构

```
ai-agent/
├── pom.xml                                           # Maven 依赖配置
├── start-local.cmd                                   # Windows 一键启动脚本
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/github/chenyouxin8/chenaiagent/
│   │   │       ├── ChenAiAgentApplication.java           # Spring Boot 启动类
│   │   │       ├── app/
│   │   │       │   └── LoveApp.java                       # 💕 恋爱顾问 Agent
│   │   │       ├── chatmemory/
│   │   │       │   └── FileBasedChatMemory.java           # 💾 文件持久化对话记忆
│   │   │       └── controller/
│   │   │           └── AiController.java                 # 通用对话 REST 接口
│   │   └── resources/
│   │       └── application.yml                           # 应用配置
│   └── test/
│       └── java/.../chenaiagent/
│           ├── app/
│           │   └── LoveAppTest.java                       # 🧪 Agent 单元测试
│           └── chatmemory/
│               └── FileBasedChatMemoryTest.java           # 🧪 记忆持久化测试
```

| 文件 | 说明 |
|------|------|
| `pom.xml` | Maven 依赖：Spring Boot 4.1、Spring AI 2.0、阿里 DashScope SDK、Knife4j、Lombok、Hutool、Kryo、JSON Schema Generator |
| `ChenAiAgentApplication.java` | Spring Boot 应用入口 |
| `LoveApp.java` | 恋爱顾问场景 Agent：角色定位为恋爱心理专家，支持多轮对话记忆 + 结构化输出（`LoveReport`） |
| `FileBasedChatMemory.java` | 基于 Kryo 序列化的文件存储实现，支持跨会话持久化、按需检索最近 N 条消息 |
| `AiController.java` | 通用对话控制器，提供 `/ai/chat` GET 接口 |
| `FileBasedChatMemoryTest.java` | 对话记忆持久化的完整单元测试：读写、隔离、持久化验证 |
| `LoveAppTest.java` | `LoveApp` 的 Mockito 单元测试 |
| `application.yml` | 配置 base-url、API Key、模型名、端口、Knife4j 中文界面设置 |
| `start-local.cmd` | Windows 启动脚本，需按本机 `JAVA_HOME` 和项目路径修改 |

---

## 💡 使用示例

### 通用对话（AiController）

```bash
curl "http://localhost:8123/api/ai/chat?message=%E4%BD%A0%E5%A5%BD%EF%BC%8C%E8%AF%B7%E5%88%86%E4%BA%AB%E4%B8%80%E4%B8%AA%E6%8A%80%E6%9C%AF%E7%9F%A5%E8%AF%86"
```

### 恋爱顾问对话（LoveApp）

```java
// 多轮对话（内存记忆）
loveApp.doChat("我喜欢一个女生，不知道怎么表白", "user-001");
loveApp.doChat("如果她拒绝了我该怎么办？", "user-001");  // 自动关联上下文

// 结构化输出
LoveReport report = loveApp.doChatWithReport("帮我分析一下我的恋爱困境", "user-001");
System.out.println(report.title());        // 输出标题
System.out.println(report.suggestions());  // 输出建议列表
```

### 文件持久化对话记忆

```java
// 创建基于文件的对话记忆（指定存储目录）
FileBasedChatMemory memory = new FileBasedChatMemory("/data/chat-memory");

// 添加消息（自动持久化到文件）
memory.add("user-001", List.of(new UserMessage("你好")));

// 获取最近 10 条消息
List<Message> recent = memory.get("user-001", 10);

// 清空对话历史
memory.clear("user-001");
```

### 在 Knife4j 文档页调试

1. 启动服务
2. 打开 http://localhost:8123/api/swagger-ui.html
3. 找到 `GET /ai/chat`
4. 填入 `message` 参数，点击"执行"

---

## 🧪 运行测试

```bash
# 运行所有测试
./mvnw test

# 仅运行 Agent 测试
./mvnw test -Dtest=LoveAppTest

# 仅运行记忆持久化测试
./mvnw test -Dtest=FileBasedChatMemoryTest
```

---

## 🤝 扩展方向

| 方向 | 说明 |
|------|------|
| REST API 化 | 将 LoveApp 暴露为独立 REST 接口，支持 Web 前端调用 |
| 数据库持久化 | 将 `FileBasedChatMemory` 改造为基于 MySQL/Redis 的存储 |
| 工具调用（Tools） | 让 Agent 调用外部工具（查天气、搜网页） |
| RAG 知识库 | 对接向量数据库，实现情感领域专业知识增强 |
| 多模型切换 | 同时支持通义千问、OpenAI 等多个模型 |
| WebSocket 流式对话 | 改为 SSE 流式输出，提升实时交互体验 |

---

## 📦 技术栈

| 分类 | 技术 |
|------|------|
| 框架 | Spring Boot 4.1 |
| AI | Spring AI 2.0 + 通义千问 Qwen-Max |
| SDK | Alibaba DashScope SDK 2.22 |
| 序列化 | Kryo 5.6（对话记忆持久化） |
| 结构化输出 | JSON Schema Generator 4.38 |
| 工具库 | Hutool 5.8、Lombok 1.18 |
| API 文档 | Knife4j 4.4 + SpringDoc OpenAPI |
| 测试 | JUnit 5 + Mockito |
| 构建 | Maven（Wrapper 内置）|

---

## 🤝 贡献

欢迎提交 Issue 或 Pull Request！

---

## 📄 License

[MIT](LICENSE) © 2026 ChenYouXin8
