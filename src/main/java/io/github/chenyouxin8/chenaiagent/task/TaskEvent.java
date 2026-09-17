package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

@Data
public class TaskEvent {
    private final String taskId;
    private final TaskEventType type;
    private final String stepId;
    private final String message;
    private final long timestamp = System.currentTimeMillis();

    public TaskEvent(String taskId, TaskEventType type, String stepId, String message) {
        this.taskId = taskId;
        this.type = type;
        this.stepId = stepId;
        this.message = message;
    }
}
