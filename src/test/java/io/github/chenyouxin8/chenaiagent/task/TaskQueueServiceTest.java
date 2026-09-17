package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskQueueServiceTest {

    @Test
    void localQueueShouldPollHigherPriorityFirstAndKeepFifoWithinPriority() {
        StringRedisTemplate redis = null;
        TaskQueueService queue = new TaskQueueService(redis, false, "test:queue");

        queue.enqueue("low", TaskPriority.LOW);
        queue.enqueue("normal-1", TaskPriority.NORMAL);
        queue.enqueue("critical", TaskPriority.CRITICAL);
        queue.enqueue("high", TaskPriority.HIGH);
        queue.enqueue("normal-2", TaskPriority.NORMAL);

        assertEquals("critical", queue.poll());
        assertEquals("high", queue.poll());
        assertEquals("normal-1", queue.poll());
        assertEquals("normal-2", queue.poll());
        assertEquals("low", queue.poll());
    }
}
