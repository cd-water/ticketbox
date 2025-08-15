package com.cdwater.ticketbox.order.domain;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyLockService {

    private static final Duration TTL = Duration.ofSeconds(15);

    private final StringRedisTemplate redis;

    public IdempotencyLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(Long userId, String idempotencyKey) {
        return Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(key(userId, idempotencyKey), "1", TTL));
    }

    public void release(Long userId, String idempotencyKey) {
        redis.delete(key(userId, idempotencyKey));
    }

    private static String key(Long userId, String idempotencyKey) {
        return "idem:" + userId + ":" + idempotencyKey;
    }
}
