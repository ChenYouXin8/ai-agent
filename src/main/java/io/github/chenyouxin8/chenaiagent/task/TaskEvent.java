package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.util.UUID;

@Data
public class TaskEvent {
    private final String eventId;
    private final String taskId;
    private final TaskEventType type;
    private final String stepId;
    private final String message;
    private final long timestamp;

    public TaskEvent(String taskId, TaskEventType type, String stepId, String message) {
        this(UUID.randomUUID().toString(), taskId, type, stepId, message, System.currentTimeMillis());
    }

    public TaskEvent(String eventId, String taskId, TaskEventType type, String stepId, String message, long timestamp) {
        this.eventId = eventId;
        this.taskId = taskId;
        this.type = type;
        this.stepId = stepId;
        this.message = message;
        this.timestamp = timestamp;
    }
}
