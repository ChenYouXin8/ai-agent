package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

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

        assertEquals(4.0, task.getEstimatedCost(), 0.00001);
        assertEquals(1_000, task.getActualInputTokens());
        assertEquals(500, task.getActualOutputTokens());
    }

    @Test
    void concurrentUsageUpdatesShouldNotLoseTaskTotals() throws Exception {
        TaskMetricsService metrics = new TaskMetricsService(0.0, 0.0);
        ChenTask task = new ChenTask("task_metrics_concurrent", "metrics");
        int workers = 8;
        int updatesPerWorker = 100;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < updatesPerWorker; j++)
                        metrics.recordActualUsage(task, 10, 20, 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }

        start.countDown();
        done.await();

        assertEquals(workers * updatesPerWorker * 10L, task.getActualInputTokens());
        assertEquals(workers * updatesPerWorker * 20L, task.getActualOutputTokens());
        assertEquals(workers * updatesPerWorker, task.getModelCallCount());
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
