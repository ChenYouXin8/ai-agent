package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisDistributedLockService {

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public RedisDistributedLockService(
            StringRedisTemplate redis,
            @Value("${chenmanus.lock.redis-enabled:false}") boolean enabled
    ) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public boolean tryLock(String resource, Duration ttl) {
        if (!enabled) return true;
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    "chenmanus:lock:" + resource, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                tokens.put(resource, token);
                return true;
            }
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    public void unlock(String resource) {
        if (!enabled) return;
        String token = tokens.remove(resource);
        if (token == null) return;
        try {
            String key = "chenmanus:lock:" + resource;
            String current = redis.opsForValue().get(key);
            if (token.equals(current)) redis.delete(key);
        } catch (RuntimeException ignored) {
            // The TTL is the final safety net when Redis is unavailable.
        }
    }
}
