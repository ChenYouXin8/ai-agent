package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TaskQueueService {

    private static final List<TaskPriority> POLL_ORDER = List.of(
            TaskPriority.CRITICAL,
            TaskPriority.HIGH,
            TaskPriority.NORMAL,
            TaskPriority.LOW
    );

    private final StringRedisTemplate redis;
    private final boolean redisEnabled;
    private final String queueKey;
    private final PriorityBlockingQueue<QueueItem> localQueue = new PriorityBlockingQueue<>();
    private final AtomicLong sequence = new AtomicLong();

    public TaskQueueService(
            StringRedisTemplate redis,
            @Value("${chenmanus.queue.redis-enabled:false}") boolean redisEnabled,
            @Value("${chenmanus.queue.key:chenmanus:tasks}") String queueKey
    ) {
        this.redis = redis;
        this.redisEnabled = redisEnabled;
        this.queueKey = queueKey;
    }

    public void enqueue(String taskId) {
        enqueue(taskId, TaskPriority.NORMAL);
    }

    public void enqueue(String taskId, TaskPriority priority) {
        TaskPriority effective = priority == null ? TaskPriority.NORMAL : priority;
        if (redisEnabled) {
            try {
                redis.opsForList().rightPush(redisKey(effective), taskId);
                return;
            } catch (RuntimeException ignored) {
                // Redis is optional in development; fall back to local queue.
            }
        }
        localQueue.offer(new QueueItem(taskId, effective, sequence.incrementAndGet()));
    }

    public String poll() {
        QueueItem local = localQueue.poll();
        if (local != null) return local.taskId();
        if (!redisEnabled) return null;
        for (TaskPriority priority : POLL_ORDER) {
            try {
                String taskId = redis.opsForList().leftPop(redisKey(priority));
                if (taskId != null) return taskId;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    private String redisKey(TaskPriority priority) {
        return queueKey + ":" + priority.name().toLowerCase();
    }

    private record QueueItem(String taskId, TaskPriority priority, long sequence) implements Comparable<QueueItem> {
        @Override
        public int compareTo(QueueItem other) {
            int byPriority = Integer.compare(other.priority.getWeight(), priority.getWeight());
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }
}
