package io.github.chenyouxin8.chenaiagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟测试：验证 Spring 上下文可在无外部服务（Chroma / Redis / MCP / 真实 API Key）的情况下离线加载。
 * 向量存储由测试侧的 SimpleVectorStore（零向量嵌入）提供，见 LoveAppVectorStoreConfig。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration",
        "spring.ai.mcp.client.enabled=false"
})
class ChenAiAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
