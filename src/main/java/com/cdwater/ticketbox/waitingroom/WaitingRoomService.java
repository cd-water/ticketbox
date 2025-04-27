package com.cdwater.ticketbox.waitingroom;

import com.cdwater.ticketbox.auth.UserAccount;
import com.cdwater.ticketbox.auth.UserAccountRepository;
import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.event.Event;
import com.cdwater.ticketbox.event.EventRepository;
import com.cdwater.ticketbox.performance.Performance;
import com.cdwater.ticketbox.performance.PerformanceRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class WaitingRoomService {

    public record Status(
            boolean enabled,
            boolean joined,
            boolean admitted,
            long position,
            long estimatedWaitSeconds,
            Instant tokenExpiresAt,
            Long eventId,
            String eventTitle,
            Instant startsAt,
            String venueName) {
    }

    private final PerformanceRepository performances;
    private final EventRepository events;
    private final UserAccountRepository users;
    private final StringRedisTemplate redis;

    public WaitingRoomService(PerformanceRepository performances,
                              EventRepository events,
                              UserAccountRepository users,
                              StringRedisTemplate redis) {
        this.performances = performances;
        this.events = events;
        this.users = users;
        this.redis = redis;
    }

    public Status join(Long performanceId, String username) {
        Performance performance = requirePerformance(performanceId);
        if (!performance.isWaitingRoomEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WAITING_ROOM_DISABLED",
                    "This performance does not use a waiting room");
        }
        UserAccount user = requireUser(username);
        String member = String.valueOf(user.getId());
        Long sequence = redis.opsForValue().increment(sequenceKey(performanceId));
        redis.opsForZSet().addIfAbsent(queueKey(performanceId), member,
                sequence == null ? System.currentTimeMillis() : sequence);
        redis.expire(queueKey(performanceId), Duration.ofHours(24));
        redis.expire(sequenceKey(performanceId), Duration.ofHours(24));
        return statusFor(performance, user.getId());
    }

    public Status status(Long performanceId, String username) {
        Performance performance = requirePerformance(performanceId);
        if (!performance.isWaitingRoomEnabled()) {
            return withEvent(performance, false, false, false, 0, 0, null);
        }
        UserAccount user = requireUser(username);
        return statusFor(performance, user.getId());
    }

    public void requireAdmission(Long performanceId, String username) {
        Performance performance = requirePerformance(performanceId);
        if (!performance.isWaitingRoomEnabled()) {
            return;
        }
        if (username == null) {
            throw tokenRequired();
        }
        UserAccount user = users.findByUsername(username).orElseThrow(WaitingRoomService::tokenRequired);
        if (!Boolean.TRUE.equals(redis.hasKey(tokenKey(performanceId, user.getId())))) {
            throw tokenRequired();
        }
    }

    private Status statusFor(Performance performance, Long userId) {
        Long performanceId = performance.getId();
        String member = String.valueOf(userId);
        Long rank = redis.opsForZSet().rank(queueKey(performanceId), member);
        if (rank == null) {
            return withEvent(performance, true, false, false, 0, 0, null);
        }

        Long size = redis.opsForZSet().zCard(queueKey(performanceId));
        long queueSize = size == null ? 0 : size;
        long elapsedMillis = Math.max(0,
                System.currentTimeMillis() - performance.getSaleStartAt().toEpochMilli());
        long elapsedSeconds = elapsedMillis / 1000;
        long admittedCount = Math.min(queueSize, elapsedSeconds * performance.getAdmissionRate());

        if (rank < admittedCount) {
            String tokenKey = tokenKey(performanceId, userId);
            redis.opsForValue().set(
                    tokenKey, "1", Duration.ofSeconds(performance.getTokenTtlSeconds()));
            return withEvent(performance, true, true, true, 0, 0,
                    Instant.now().plusSeconds(performance.getTokenTtlSeconds()));
        }

        long position = rank + 1 - admittedCount;
        long waitSeconds = (long) Math.ceil((double) position / performance.getAdmissionRate());
        return withEvent(performance, true, true, false, position, waitSeconds, null);
    }

    private Status withEvent(Performance performance, boolean enabled, boolean joined, boolean admitted,
                             long position, long waitSeconds, Instant tokenExpiresAt) {
        Event event = events.findById(performance.getEventId()).orElse(null);
        return new Status(
                enabled,
                joined,
                admitted,
                position,
                waitSeconds,
                tokenExpiresAt,
                event == null ? null : event.getId(),
                event == null ? null : event.getTitle(),
                performance.getStartsAt(),
                performance.getVenueName());
    }

    private Performance requirePerformance(Long performanceId) {
        return performances.findById(performanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND",
                        "Performance not found"));
    }

    private UserAccount requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authentication required"));
    }

    private static ApiException tokenRequired() {
        return new ApiException(HttpStatus.CONFLICT, "TOKEN_REQUIRED",
                "Join the waiting room before selecting seats");
    }

    private static String queueKey(Long performanceId) {
        return "wr:queue:" + performanceId;
    }

    private static String sequenceKey(Long performanceId) {
        return "wr:seq:" + performanceId;
    }

    private static String tokenKey(Long performanceId, Long userId) {
        return "wr:token:" + performanceId + ":" + userId;
    }
}
