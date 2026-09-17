package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChenTask {
    private final String taskId;
    private final String prompt;
    private String title;
    private volatile TaskStatus status = TaskStatus.CREATED;
    private final List<TaskStep> steps = new ArrayList<>();
    private final long createdAt = System.currentTimeMillis();
    private volatile long updatedAt = createdAt;
    private volatile String result;
    private volatile String error;

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }
}
