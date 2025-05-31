package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.ticket.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeatAvailability {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final TicketRepository tickets;
    private final Duration ttl;

    public SeatAvailability(StringRedisTemplate redis,
                            TicketRepository tickets,
                            @Value("${app.order.expiry:15m}") Duration expiry) {
        this.redis = redis;
        this.tickets = tickets;
        // Outlive the order so the database sweep wins the race against TTL expiry.
        this.ttl = expiry.plusMinutes(2);
    }

    public boolean lockAll(Long performanceId, Collection<Long> seatIds, String token) {
        List<Long> sorted = seatIds.stream().sorted().toList();
        List<Long> acquired = new ArrayList<>(sorted.size());
        for (Long seatId : sorted) {
            Boolean locked = redis.opsForValue().setIfAbsent(key(performanceId, seatId), token, ttl);
            if (Boolean.TRUE.equals(locked)) {
                acquired.add(seatId);
            } else {
                release(performanceId, acquired, token);
                return false;
            }
        }
        return true;
    }

    public void release(Long performanceId, Collection<Long> seatIds, String token) {
        for (Long seatId : seatIds) {
            redis.execute(RELEASE_SCRIPT, List.of(key(performanceId, seatId)), token);
        }
    }

    public void recover(Long performanceId, Collection<Long> seatIds, String token) {
        for (Long seatId : seatIds) {
            redis.opsForValue().setIfAbsent(key(performanceId, seatId), token, ttl);
        }
    }

    public Map<Long, SeatStatus> statuses(Long performanceId, Collection<Long> seatIds) {
        Map<Long, SeatStatus> statuses = new HashMap<>(seatIds.size());
        if (seatIds.isEmpty()) {
            return statuses;
        }
        Set<Long> sold = tickets.findSeatIdsByPerformanceId(performanceId);
        List<Long> ordered = List.copyOf(seatIds);
        List<String> values = redis.opsForValue().multiGet(
                ordered.stream().map(seatId -> key(performanceId, seatId)).toList());
        for (int index = 0; index < ordered.size(); index++) {
            Long seatId = ordered.get(index);
            SeatStatus status;
            if (sold.contains(seatId)) {
                status = SeatStatus.SOLD;
            } else if (values != null && values.get(index) != null) {
                status = SeatStatus.LOCKED;
            } else {
                status = SeatStatus.AVAILABLE;
            }
            statuses.put(seatId, status);
        }
        return statuses;
    }

    private static String key(Long performanceId, Long seatId) {
        return "seat:" + performanceId + ":" + seatId;
    }
}
