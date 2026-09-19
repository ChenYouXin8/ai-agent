package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskMetricsService {

    private final double inputCostPer1k;
    private final double outputCostPer1k;

    public TaskMetricsService(
            @Value("${chenmanus.metrics.input-cost-per-1k:0.0}") double inputCostPer1k,
            @Value("${chenmanus.metrics.output-cost-per-1k:0.0}") double outputCostPer1k
    ) {
        this.inputCostPer1k = inputCostPer1k;
        this.outputCostPer1k = outputCostPer1k;
    }

    public void startTask(ChenTask task) {
        if (task.getStartedAt() == 0L) task.setStartedAt(System.currentTimeMillis());
    }

    public void finishTask(ChenTask task) {
        task.setCompletedAt(System.currentTimeMillis());
        task.setDurationMs(Math.max(0L, task.getCompletedAt() - task.getStartedAt()));
        long inputTokens = task.getActualInputTokens() > 0 ? task.getActualInputTokens() : task.getEstimatedInputTokens();
        long outputTokens = task.getActualOutputTokens() > 0 ? task.getActualOutputTokens() : task.getEstimatedOutputTokens();
        task.setEstimatedCost(
                inputTokens / 1000.0 * inputCostPer1k
                        + outputTokens / 1000.0 * outputCostPer1k
        );
    }

    public void recordStep(ChenTask task, TaskStep step, String prompt, String output) {
        long inputTokens = estimateTokens(prompt);
        long outputTokens = estimateTokens(output);
        synchronized (task) {
            step.setEstimatedInputTokens(step.getEstimatedInputTokens() + inputTokens);
            step.setEstimatedOutputTokens(step.getEstimatedOutputTokens() + outputTokens);
            task.setEstimatedInputTokens(task.getEstimatedInputTokens() + inputTokens);
            task.setEstimatedOutputTokens(task.getEstimatedOutputTokens() + outputTokens);
        }
    }

    public void recordActualUsage(ChenTask task, TaskStep step, long inputTokens, long outputTokens, long modelCalls) {
        long safeInput = Math.max(0L, inputTokens);
        long safeOutput = Math.max(0L, outputTokens);
        long safeCalls = Math.max(0L, modelCalls);
        synchronized (task) {
            step.setActualInputTokens(step.getActualInputTokens() + safeInput);
            step.setActualOutputTokens(step.getActualOutputTokens() + safeOutput);
            step.setModelCallCount(step.getModelCallCount() + safeCalls);
            recordActualUsage(task, safeInput, safeOutput, safeCalls);
        }
    }

    public void recordActualUsage(ChenTask task, long inputTokens, long outputTokens, long modelCalls) {
        synchronized (task) {
            task.setActualInputTokens(task.getActualInputTokens() + Math.max(0L, inputTokens));
            task.setActualOutputTokens(task.getActualOutputTokens() + Math.max(0L, outputTokens));
            task.setModelCallCount(task.getModelCallCount() + Math.max(0L, modelCalls));
        }
    }

    public long estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0L;
        return Math.max(1L, (text.length() + 3L) / 4L);
    }
}
