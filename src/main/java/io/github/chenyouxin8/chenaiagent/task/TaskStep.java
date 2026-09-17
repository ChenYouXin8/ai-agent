package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.io.Serializable;

@Data
public class TaskStep implements Serializable {
    private static final long serialVersionUID = 1L;

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
    private boolean parallelizable;

    public TaskStep(String stepId, int sequence, String title, String description) {
        this(stepId, sequence, title, description, false);
    }

    public TaskStep(String stepId, int sequence, String title, String description, boolean parallelizable) {
        this.stepId = stepId;
        this.sequence = sequence;
        this.title = title;
        this.description = description;
        this.parallelizable = parallelizable;
    }
}
