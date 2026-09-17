package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskMetricsServiceTest {

    @Test
    void shouldPreferActualUsageForCostWhenAvailable() {
        TaskMetricsService metrics = new TaskMetricsService(2.0, 4.0);
        ChenTask task = new ChenTask("task_metrics", "metrics");
        task.setStartedAt(1_000L);
        task.setEstimatedInputTokens(10_000);
        task.setEstimatedOutputTokens(10_000);
        task.setActualInputTokens(1_000);
        task.setActualOutputTokens(500);

        metrics.finishTask(task);

        assertEquals(6.0, task.getEstimatedCost(), 0.00001);
        assertEquals(1_000, task.getActualInputTokens());
        assertEquals(500, task.getActualOutputTokens());
    }

    @Test
    void shouldUseEstimatedUsageAsFallback() {
        TaskMetricsService metrics = new TaskMetricsService(2.0, 4.0);
        ChenTask task = new ChenTask("task_metrics_fallback", "metrics");
        task.setStartedAt(1_000L);
        task.setEstimatedInputTokens(1_000);
        task.setEstimatedOutputTokens(500);

        metrics.finishTask(task);

        assertEquals(4.0, task.getEstimatedCost(), 0.00001);
    }
}
