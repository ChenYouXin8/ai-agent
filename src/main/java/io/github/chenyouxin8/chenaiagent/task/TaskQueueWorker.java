package io.github.chenyouxin8.chenaiagent.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class TaskQueueWorker {

    private final TaskQueueService queue;
    private final RedisDistributedLockService lockService;
    private final TaskRuntimeService runtime;
    private final TaskManager taskManager;

    public TaskQueueWorker(
            TaskQueueService queue,
            RedisDistributedLockService lockService,
            TaskRuntimeService runtime,
            TaskManager taskManager
    ) {
        this.queue = queue;
        this.lockService = lockService;
        this.runtime = runtime;
        this.taskManager = taskManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverUnfinishedTasks() {
        taskManager.list().stream()
                .filter(task -> task.getStatus() == TaskStatus.QUEUED
                        || task.getStatus() == TaskStatus.PLANNING
                        || task.getStatus() == TaskStatus.RUNNING
                        || task.getStatus() == TaskStatus.REVIEWING)
                .forEach(task -> queue.enqueue(task.getTaskId()));
    }

    @Scheduled(fixedDelayString = "${chenmanus.queue.poll-ms:250}")
    public void poll() {
        String taskId = queue.poll();
        if (taskId == null) return;

        // Duplicate queue messages are harmless: only the lock holder executes the task.
        if (!lockService.tryLock(taskId, Duration.ofMinutes(15))) return;
        try {
            runtime.runNow(taskId);
        } catch (RuntimeException e) {
            log.error("ChenManus task worker failed for {}", taskId, e);
        } finally {
            lockService.unlock(taskId);
        }
    }
}
