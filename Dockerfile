# ========== Stage 1: Build ==========
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# 拷贝 pom.xml 先（利用 Docker 缓存层）
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# 下载依赖（改动 pom.xml 前会复用这层缓存）
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# 拷贝源码并构建
COPY src src
RUN ./mvnw package -DskipTests -B

# ========== Stage 2: Runtime ==========
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户（安全最佳实践）
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -s /bin/sh -D appuser

# 安装 curl（健康检查用到）
RUN apk add --no-cache curl

COPY --from=builder /app/target/*.jar app.jar

# 切换到普通用户
USER appuser

# ---- 环境变量（运行时覆盖）----
# AI_DASHSCOPE_API_KEY   : 必填，通义千问 API Key
# API_KEY                 : 可选，生产环境 Bearer Token 鉴权
# SEARCH_API_KEY          : 可选，searchapi.io Key
# SPRING_PROFILES_ACTIVE  : 默认 local（加载 application-local.yml）

ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8123

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8123/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
