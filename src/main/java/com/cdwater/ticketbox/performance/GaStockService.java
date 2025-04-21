package com.cdwater.ticketbox.performance;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class GaStockService {

    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redis;

    public GaStockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryReserve(Long performanceId, Long priceTierId, int quantity, int dbStock) {
        String key = key(performanceId, priceTierId);
        redis.opsForValue().setIfAbsent(key, String.valueOf(dbStock), TTL);
        Long remaining = redis.opsForValue().decrement(key, quantity);
        if (remaining == null || remaining < 0) {
            redis.opsForValue().increment(key, quantity);
            redis.expire(key, TTL);
            return false;
        }
        redis.expire(key, TTL);
        return true;
    }

    public void release(Long performanceId, Long priceTierId, int quantity) {
        String key = key(performanceId, priceTierId);
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            // Redis lost the counter; the next reservation rebuilds it from MySQL.
            return;
        }
        redis.opsForValue().increment(key, quantity);
        redis.expire(key, TTL);
    }

    public void reset(Long performanceId, Long priceTierId, int stock) {
        redis.opsForValue().set(key(performanceId, priceTierId), String.valueOf(stock), TTL);
    }

    private static String key(Long performanceId, Long priceTierId) {
        return "stock:" + performanceId + ":" + priceTierId;
    }
}
