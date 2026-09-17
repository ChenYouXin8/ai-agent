package io.github.chenyouxin8.chenaiagent.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class TaskQueueWorker {

    private final TaskQueueService queue;
    private final RedisDistributedLockService lockService;
    private final TaskRuntimeService runtime;

    public TaskQueueWorker(
            TaskQueueService queue,
            RedisDistributedLockService lockService,
            TaskRuntimeService runtime
    ) {
        this.queue = queue;
        this.lockService = lockService;
        this.runtime = runtime;
    }

    @Scheduled(fixedDelayString = "${chenmanus.queue.poll-ms:250}")
    public void poll() {
        String taskId = queue.poll();
        if (taskId == null) return;

        if (!lockService.tryLock(taskId, Duration.ofMinutes(15))) {
            queue.enqueue(taskId);
            return;
        }
        try {
            runtime.runNow(taskId);
        } catch (RuntimeException e) {
            log.error("ChenManus task worker failed for {}", taskId, e);
        } finally {
            lockService.unlock(taskId);
        }
    }
}
