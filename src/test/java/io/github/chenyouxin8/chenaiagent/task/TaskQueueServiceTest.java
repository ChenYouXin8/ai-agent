package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertNull(queue.poll());
    }

    @Test
    void localDeadLetterQueueShouldRetainTaskAndReason() {
        TaskQueueService queue = new TaskQueueService(null, false, "test:queue");

        queue.deadLetter("task-1", "模型连续失败");
        assertEquals(1, queue.deadLetters(10).size());
        assertEquals("task-1|模型连续失败", queue.deadLetters(10).get(0));
        assertEquals("task-1|模型连续失败", queue.pollDeadLetter());
        assertNull(queue.pollDeadLetter());
    }
}
