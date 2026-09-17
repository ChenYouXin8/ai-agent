package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ChenTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String taskId;
    private final String prompt;
    private String title;
    private volatile TaskStatus status = TaskStatus.CREATED;
    private final List<TaskStep> steps = new ArrayList<>();
    private final List<Artifact> artifacts = new ArrayList<>();
    private long createdAt = System.currentTimeMillis();
    private volatile long updatedAt = createdAt;
    private volatile long startedAt;
    private volatile long completedAt;
    private volatile long durationMs;
    private volatile long estimatedInputTokens;
    private volatile long estimatedOutputTokens;
    private volatile double estimatedCost;
    private volatile String result;
    private volatile String error;
    private volatile String planSummary;
    private volatile ReviewDecision review;
    private String ownerId = "anonymous";
    private String sessionId = "default";

    public ChenTask(String taskId, String prompt) {
        this.taskId = taskId;
        this.prompt = prompt;
    }

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }
}
