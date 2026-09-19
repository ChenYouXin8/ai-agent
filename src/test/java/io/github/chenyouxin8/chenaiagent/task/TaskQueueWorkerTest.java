package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskQueueWorkerTest {

    @Test
    void lockContentionRequeuesTaskWithOriginalPriority() {
        TaskQueueService queue = mock(TaskQueueService.class);
        RedisDistributedLockService lockService = mock(RedisDistributedLockService.class);
        TaskRuntimeService runtime = mock(TaskRuntimeService.class);
        TaskManager taskManager = mock(TaskManager.class);

        ChenTask task = new ChenTask("task-lock", "锁竞争");
        task.setStatus(TaskStatus.QUEUED);
        task.setPriority(TaskPriority.HIGH);

        when(queue.poll()).thenReturn("task-lock");
        when(taskManager.get("task-lock")).thenReturn(task);
        when(lockService.tryLock("task-lock", Duration.ofMinutes(15))).thenReturn(false);

        new TaskQueueWorker(queue, lockService, runtime, taskManager).poll();

        verify(queue).enqueue("task-lock", TaskPriority.HIGH);
        verify(runtime, never()).runNow("task-lock");
        verify(queue, never()).deadLetter(any(), any());
        verify(lockService, never()).unlock("task-lock");
    }
}
