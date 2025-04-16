package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.seat.SeatLockKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class SeatLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SeatLockService(StringRedisTemplate redis, @Value("${app.order.expiry:15m}") Duration expiry) {
        this.redis = redis;
        // Outlive the order so the database sweep wins the race against TTL expiry.
        this.ttl = expiry.plusMinutes(2);
    }

    public boolean lockAll(Long performanceId, Collection<Long> seatIds, String token) {
        List<Long> sorted = seatIds.stream().sorted().toList();
        List<Long> acquired = new ArrayList<>(sorted.size());
        for (Long seatId : sorted) {
            Boolean locked = redis.opsForValue().setIfAbsent(SeatLockKeys.of(performanceId, seatId), token, ttl);
            if (Boolean.TRUE.equals(locked)) {
                acquired.add(seatId);
            } else {
                release(performanceId, acquired, token);
                return false;
            }
        }
        return true;
    }

    public void recover(Long performanceId, Collection<Long> seatIds, String token) {
        for (Long seatId : seatIds) {
            redis.opsForValue().setIfAbsent(SeatLockKeys.of(performanceId, seatId), token, ttl);
        }
    }

    public void release(Long performanceId, Collection<Long> seatIds, String token) {
        for (Long seatId : seatIds) {
            redis.execute(RELEASE_SCRIPT, List.of(SeatLockKeys.of(performanceId, seatId)), token);
        }
    }
}
