package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

@Data
public class TaskStep {
    private final String stepId;
    private final int sequence;
    private final String title;
    private final String description;
    private StepStatus status = StepStatus.PENDING;
    private String output;
    private String error;
    private long startedAt;
    private long completedAt;
    private int retryCount;

    public TaskStep(String stepId, int sequence, String title, String description) {
        this.stepId = stepId;
        this.sequence = sequence;
        this.title = title;
        this.description = description;
    }
}
