package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class TaskQueueService {

    private final StringRedisTemplate redis;
    private final boolean redisEnabled;
    private final String queueKey;
    private final BlockingQueue<String> localQueue = new LinkedBlockingQueue<>();

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
        if (redisEnabled) {
            try {
                redis.opsForList().rightPush(queueKey, taskId);
                return;
            } catch (RuntimeException ignored) {
                // Redis is optional in development; fall back to local queue.
            }
        }
        localQueue.offer(taskId);
    }

    public String poll() {
        String local = localQueue.poll();
        if (local != null) return local;
        if (!redisEnabled) return null;
        try {
            return redis.opsForList().leftPop(queueKey, Duration.ofMillis(100));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }
}
