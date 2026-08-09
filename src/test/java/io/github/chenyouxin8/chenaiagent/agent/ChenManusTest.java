package io.github.chenyouxin8.chenaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.ai.mcp.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration"
})
class ChenManusTest {

    @Resource
    private ChenManus chenManus;

    @Test
    void run() {
        String userPrompt = """  
                我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点，  
                并结合一些网络图片，制定一份详细的约会计划，  
                并以 PDF 格式输出""";
        String answer = chenManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
