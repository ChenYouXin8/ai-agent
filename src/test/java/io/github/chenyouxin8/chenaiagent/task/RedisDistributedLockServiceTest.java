package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisDistributedLockServiceTest {

    @Test
    void disabledLockAlwaysAcquiresWithoutTouchingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisDistributedLockService service = new RedisDistributedLockService(redis, false);

        assertTrue(service.tryLock("task-1", Duration.ofMinutes(1)));
        assertDoesNotThrow(() -> service.unlock("task-1"));

        verify(redis, never()).opsForValue();
    }

    @Test
    void tryLockAcquiresWhenRedisAssignsTheKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        RedisDistributedLockService service = new RedisDistributedLockService(redis, true);

        assertTrue(service.tryLock("task-1", Duration.ofMinutes(15)));
    }

    @Test
    void tryLockFailsWhenKeyAlreadyHeld() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        RedisDistributedLockService service = new RedisDistributedLockService(redis, true);

        assertFalse(service.tryLock("task-1", Duration.ofMinutes(15)));
    }

    @Test
    void tryLockFailsClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        RedisDistributedLockService service = new RedisDistributedLockService(redis, true);

        assertFalse(service.tryLock("task-1", Duration.ofMinutes(15)));
    }

    @Test
    void unlockDeletesOnlyViaCasScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        RedisDistributedLockService service = new RedisDistributedLockService(redis, true);

        assertTrue(service.tryLock("task-1", Duration.ofMinutes(15)));
        service.unlock("task-1");

        verify(redis).execute(any(RedisScript.class), eq(List.of("chenmanus:lock:task-1")), any());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void unlockWithoutHeldLockIsNoop() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisDistributedLockService service = new RedisDistributedLockService(redis, true);

        assertDoesNotThrow(() -> service.unlock("task-1"));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void unlockSwallowsRedisFailureAndReliesOnTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        RedisDistributedLockService service = new RedisDistributedLockService(redis, true);

        assertTrue(service.tryLock("task-1", Duration.ofMinutes(15)));
        assertDoesNotThrow(() -> service.unlock("task-1"));
    }
}
