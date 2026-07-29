# 🤖 Chen Ai Agent

> **基于 Spring AI + 通义千问的轻量级 AI 对话 Agent。**

Chen Ai Agent 是一个由 Spring Boot 驱动的 AI 对话服务，通过 [Spring AI](https://spring.io/projects/spring-ai) 框架接入**阿里云通义千问（Qwen-Max）**模型，提供简洁的 RESTful 对话接口。支持本地启动、API 文档自动生成，开箱即用。

[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen?style=flat-square&logo=spring)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-blue?style=flat-square&logo=spring)](https://github.com/spring-projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

---

## ✨ 核心特性

### 🧠 Spring AI 原生集成

基于 Spring AI 官方 starter 对接通义千问，无需自行处理 API 签名、请求封装等底层逻辑，几行配置即可完成 AI 能力接入。

### 🏃 一键本地启动

项目内置 Maven Wrapper (`mvnw`)，无需手动安装 Maven，在 Windows 下双击脚本即可启动服务。

### 📖 自动 API 文档

集成 **Knife4j** + **SpringDoc OpenAPI**，服务启动后自动生成交互式 API 文档，浏览器直接调试对话接口。

### ⚡ 极简对话接口

提供单一 REST 端点 `/api/ai/chat`，POST 一个问题，立即获得 AI 回复。

### 🔧 灵活配置

所有配置集中管理于 `application.yml`，支持多环境切换（local / prod）。

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

> `start-local.cmd` 会自动设置 `JAVA_HOME` 并以 `local` profile 启动。

**方式二：命令行启动**

```bash
# 克隆仓库
git clone https://github.com/ChenYouXin8/ai-agent.git
cd ai-agent

# 设置 API Key
export AI_DASHSCOPE_API_KEY=your_api_key_here

# 启动服务（Linux/Mac）
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### 验证服务

服务启动后，访问以下地址：

| 地址 | 说明 |
|------|------|
| http://localhost:8123/api/ai/chat?message=你好 | 对话接口 |
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
├── pom.xml                                    # Maven 依赖配置
├── start-local.cmd                            # Windows 一键启动脚本
├── src/
│   └── main/
│       ├── java/
│       │   └── io/github/chenyouxin8/chenaiagent/
│       │       ├── ChenAiAgentApplication.java   # Spring Boot 启动类
│       │       └── controller/
│       │           └── AiController.java         # 对话 REST 接口
│       └── resources/
│           └── application.yml                   # 应用配置
```

| 文件 | 说明 |
|------|------|
| `pom.xml` | Maven 依赖：Spring Boot、Spring AI、阿里 DashScope SDK、Knife4j、Lombok |
| `ChenAiAgentApplication.java` | Spring Boot 应用入口，标注 `@SpringBootApplication` |
| `AiController.java` | 对话控制器，提供 `/ai/chat` GET 接口，内部注册 `SimpleLoggerAdvisor` 打印日志 |
| `application.yml` | 配置 base-url、API Key、模型名、端口、API 文档路径 |
| `start-local.cmd` | Windows 启动脚本，内置 `JAVA_HOME` 路径，需按需修改 |

---

## 💡 使用示例

### cURL 调用

```bash
curl "http://localhost:8123/api/ai/chat?message=%E4%BD%A0%E5%A5%BD%EF%BC%8C%E8%AF%B7%E5%88%86%E4%BA%AB%E4%B8%80%E4%B8%AA%E6%8A%80%E6%9C%AF%E7%9F%A5%E8%AF%86"
```

### 在 Knife4j 文档页调试

1. 启动服务
2. 打开 http://localhost:8123/api/swagger-ui.html
3. 找到 `GET /ai/chat`
4. 填入 `message` 参数，点击"执行"
5. 即可在页面查看请求/响应详情

---

## 🤝 扩展方向

| 方向 | 说明 |
|------|------|
| 多轮对话 | 引入 Memory/History 管理对话上下文 |
| 工具调用（Tools） | 让 AI 调用外部工具（查天气、搜网页） |
| RAG 知识库 | 对接向量数据库，实现私有知识问答 |
| 多模型切换 | 同时支持通义千问、OpenAI 等多个模型 |
| WebSocket 实时对话 | 改为流式输出（SSE），提升交互体验 |

---

## 📦 技术栈

| 分类 | 技术 |
|------|------|
| 框架 | Spring Boot 4.1 |
| AI | Spring AI 2.0 + 通义千问 Qwen-Max |
| SDK | Alibaba DashScope SDK 2.22 |
| 工具库 | Hutool 5.8、Lombok 1.18 |
| API 文档 | Knife4j 4.4 + SpringDoc OpenAPI |
| 构建 | Maven（Wrapper 内置）|

---

## 🤝 贡献

欢迎提交 Issue 或 Pull Request！

---

## 📄 License

[MIT](LICENSE) © 2026 ChenYouXin8
