package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
    private long durationMs;
    private int retryCount;
    private boolean parallelizable;
    private List<Integer> dependsOn = new ArrayList<>();
    private long estimatedInputTokens;
    private long estimatedOutputTokens;

    public TaskStep(String stepId, int sequence, String title, String description) {
        this(stepId, sequence, title, description, false, List.of());
    }

    public TaskStep(String stepId, int sequence, String title, String description, boolean parallelizable) {
        this(stepId, sequence, title, description, parallelizable, List.of());
    }

    public TaskStep(String stepId, int sequence, String title, String description, boolean parallelizable, List<Integer> dependsOn) {
        this.stepId = stepId;
        this.sequence = sequence;
        this.title = title;
        this.description = description;
        this.parallelizable = parallelizable;
        if (dependsOn != null) this.dependsOn = new ArrayList<>(dependsOn);
    }
}
