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
                .forEach(task -> queue.enqueue(task.getTaskId(), task.getPriority()));
    }

    @Scheduled(fixedDelayString = "${chenmanus.queue.poll-ms:250}")
    public void poll() {
        String taskId = queue.poll();
        if (taskId == null) return;

        ChenTask task;
        try {
            task = taskManager.get(taskId);
        } catch (RuntimeException e) {
            log.warn("Ignoring stale ChenManus queue item {}", taskId);
            return;
        }
        if (task.getStatus() != TaskStatus.QUEUED
                && task.getStatus() != TaskStatus.PLANNING
                && task.getStatus() != TaskStatus.RUNNING
                && task.getStatus() != TaskStatus.REVIEWING) {
            return;
        }

        if (!lockService.tryLock(taskId, Duration.ofMinutes(15))) {
            // queue.poll() 已经取走元素；抢锁失败时必须放回，否则锁竞争/Redis 短暂异常会让任务失去后续执行机会
            queue.enqueue(taskId, task.getPriority());
            return;
        }
        try {
            runtime.runNow(taskId);
            ChenTask finished = taskManager.get(taskId);
            if (finished.getStatus() == TaskStatus.FAILED) {
                queue.deadLetter(taskId, finished.getError());
                taskManager.publish(new TaskEvent(taskId, TaskEventType.TASK_DEAD_LETTERED, null,
                        "任务已进入死信队列，可由管理员重放"));
            }
        } catch (RuntimeException e) {
            log.error("ChenManus task worker failed for {}", taskId, e);
            queue.deadLetter(taskId, e.getMessage());
            taskManager.publish(new TaskEvent(taskId, TaskEventType.TASK_DEAD_LETTERED, null,
                    "Worker 异常，任务已进入死信队列"));
        } finally {
            lockService.unlock(taskId);
        }
    }
}
