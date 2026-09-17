package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class TaskMemoryService {

    private final Path memoryDir;
    private final VectorStore vectorStore;

    public TaskMemoryService(
            @Value("${chenmanus.memory.dir:./data/task-memory}") String memoryDir,
            VectorStore vectorStore
    ) {
        this.memoryDir = Path.of(memoryDir);
        this.vectorStore = vectorStore;
    }

    public void remember(ChenTask task) {
        String content = buildContent(task);
        try {
            Files.createDirectories(memoryDir);
            Files.writeString(
                    memoryDir.resolve(task.getTaskId() + ".md"),
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ignored) {
            // File memory is best-effort.
        }

        try {
            vectorStore.add(List.of(new Document(
                    content,
                    Map.of(
                            "memory_type", "task_memory",
                            "task_id", task.getTaskId(),
                            "tenant_id", safe(task.getTenantId(), "default"),
                            "owner_id", safe(task.getOwnerId(), "anonymous"),
                            "session_id", safe(task.getSessionId(), "default")
                    )
            )));
        } catch (RuntimeException ignored) {
            // Semantic memory is best-effort; file memory remains available.
        }
    }

    public String recallContext(String query, int limit) {
        return recallContext(null, null, null, query, limit);
    }

    public String recallContext(String sessionId, String query, int limit) {
        return recallContext(null, null, sessionId, query, limit);
    }

    public String recallContext(String ownerId, String sessionId, String query, int limit) {
        return recallContext(null, ownerId, sessionId, query, limit);
    }

    public String recallContext(String tenantId, String ownerId, String sessionId, String query, int limit) {
        int topK = Math.max(1, Math.min(limit, 8));
        try {
            String filter = "memory_type == 'task_memory'";
            if (tenantId != null && !tenantId.isBlank()) {
                filter += " && tenant_id == '" + escapeFilter(tenantId) + "'";
            }
            if (ownerId != null && !ownerId.isBlank()) {
                filter += " && owner_id == '" + escapeFilter(ownerId) + "'";
            }
            if (sessionId != null && !sessionId.isBlank()) {
                filter += " && session_id == '" + escapeFilter(sessionId) + "'";
            }
            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query == null || query.isBlank() ? "ChenManus task" : query)
                            .topK(topK)
                            .similarityThreshold(0.15)
                            .filterExpression(filter)
                            .build()
            );
            if (documents != null && !documents.isEmpty()) {
                return documents.stream()
                        .map(Document::getText)
                        .filter(text -> text != null && !text.isBlank())
                        .map(text -> truncate(text, 2200))
                        .reduce((a, b) -> a + "\n\n--- 相关历史任务 ---\n\n" + b)
                        .orElse("");
            }
        } catch (RuntimeException ignored) {
            // Fall through to lexical file memory.
        }
        return lexicalRecall(tenantId, ownerId, sessionId, query, topK);
    }

    private String lexicalRecall(String tenantId, String ownerId, String sessionId, String query, int limit) {
        if (!Files.isDirectory(memoryDir)) return "";
        List<Path> files;
        try (var stream = Files.list(memoryDir)) {
            files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .limit(Math.max(1, limit) * 5L)
                    .toList();
        } catch (IOException e) {
            return "";
        }

        String normalizedQuery = query == null ? "" : query.toLowerCase();
        String normalizedTenant = tenantId == null ? "" : tenantId.trim();
        String normalizedOwner = ownerId == null ? "" : ownerId.trim();
        String normalizedSession = sessionId == null ? "" : sessionId.trim();
        List<String> hits = new ArrayList<>();
        for (Path file : files) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                // Tenant is mandatory for scoped recall. Legacy memories without tenant metadata
                // remain readable only through the explicit compatibility overloads above.
                if (!normalizedTenant.isBlank() && !content.contains("租户：" + normalizedTenant + "\n")) continue;
                if (!normalizedOwner.isBlank() && !content.contains("用户：" + normalizedOwner + "\n")) continue;
                if (!normalizedSession.isBlank() && !content.contains("会话：" + normalizedSession + "\n")) continue;
                if (normalizedQuery.isBlank() || containsToken(content.toLowerCase(), normalizedQuery)) {
                    hits.add(truncate(content, 1800));
                    if (hits.size() >= limit) break;
                }
            } catch (IOException ignored) {
                // Continue with other memory records.
            }
        }
        return String.join("\n\n--- 过去任务记忆 ---\n\n", hits);
    }

    private String buildContent(ChenTask task) {
        String title = task.getTitle() == null ? "ChenManus Task" : task.getTitle();
        String result = task.getResult() == null ? "" : truncate(task.getResult(), 5000);
        String feedback = task.getReview() == null ? "" : truncate(task.getReview().feedback(), 1000);
        return "租户：" + safe(task.getTenantId(), "default") + "\n"
                + "用户：" + safe(task.getOwnerId(), "anonymous") + "\n"
                + "会话：" + safe(task.getSessionId(), "default") + "\n"
                + "任务：" + title + "\n"
                + "目标：" + task.getPrompt() + "\n"
                + "结果：" + result + "\n"
                + "审核：" + feedback + "\n";
    }

    private boolean containsToken(String content, String query) {
        if (content.contains(query)) return true;
        for (String token : query.split("\\s+")) {
            if (token.length() >= 2 && content.contains(token)) return true;
        }
        for (int i = 0; i + 1 < query.length(); i++) {
            String bigram = query.substring(i, i + 2);
            if (bigram.trim().length() == 2 && content.contains(bigram)) return true;
        }
        return false;
    }

    private String escapeFilter(String value) {
        return value.replace("'", "''");
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
