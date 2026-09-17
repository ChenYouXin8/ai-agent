package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskMemoryTenantIsolationTest {

    private final VectorStore vectorStore = mock(VectorStore.class);

    @AfterEach
    void clearTenant() {
        TaskTenantContext.clear();
    }

    @Test
    void lexicalFallbackOnlyReturnsMemoryFromCurrentTenant() throws Exception {
        Path memoryDir = Files.createTempDirectory("chenmanus-memory-test");
        Files.writeString(memoryDir.resolve("tenant-a.md"),
                "租户：tenant-a\n用户：user-a\n会话：session-a\n任务：A\n目标：shared goal\n结果：A private result\n",
                StandardCharsets.UTF_8);
        Files.writeString(memoryDir.resolve("tenant-b.md"),
                "租户：tenant-b\n用户：user-a\n会话：session-a\n任务：B\n目标：shared goal\n结果：B private result\n",
                StandardCharsets.UTF_8);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("vector offline"));

        TaskMemoryService service = new TaskMemoryService(memoryDir.toString(), vectorStore);
        TaskTenantContext.set("tenant-a");

        String result = service.recallContext("user-a", "session-a", "shared goal", 5);

        assertTrue(result.contains("A private result"));
        assertFalse(result.contains("B private result"));
    }
}
