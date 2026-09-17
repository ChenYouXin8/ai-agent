package io.github.chenyouxin8.chenaiagent.task;

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

@Service
public class TaskMemoryService {

    private final Path memoryDir;

    public TaskMemoryService(@Value("${chenmanus.memory.dir:./data/task-memory}") String memoryDir) {
        this.memoryDir = Path.of(memoryDir);
    }

    public void remember(ChenTask task) {
        try {
            Files.createDirectories(memoryDir);
            String title = task.getTitle() == null ? "ChenManus Task" : task.getTitle();
            String result = task.getResult() == null ? "" : truncate(task.getResult(), 5000);
            String feedback = task.getReview() == null ? "" : truncate(task.getReview().feedback(), 1000);
            String content = "用户：" + safe(task.getOwnerId(), "anonymous") + "\n"
                    + "会话：" + safe(task.getSessionId(), "default") + "\n"
                    + "任务：" + title + "\n"
                    + "目标：" + task.getPrompt() + "\n"
                    + "结果：" + result + "\n"
                    + "审核：" + feedback + "\n";
            Files.writeString(
                    memoryDir.resolve(task.getTaskId() + ".md"),
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ignored) {
            // Memory is an enhancement and must not make a task fail.
        }
    }

    public String recallContext(String query, int limit) {
        return recallContext(null, query, limit);
    }

    public String recallContext(String sessionId, String query, int limit) {
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
        String normalizedSession = sessionId == null ? "" : sessionId.trim();
        List<String> hits = new ArrayList<>();
        for (Path file : files) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (!normalizedSession.isBlank() && !content.contains("会话：" + normalizedSession + "\n")) continue;
                if (normalizedQuery.isBlank() || containsToken(content.toLowerCase(), normalizedQuery)) {
                    hits.add(truncate(content, 1800));
                    if (hits.size() >= Math.max(1, limit)) break;
                }
            } catch (IOException ignored) {
                // Continue with other memory records.
            }
        }
        return String.join("\n\n--- 过去任务记忆 ---\n\n", hits);
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
