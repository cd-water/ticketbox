package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeatAvailabilityTest extends ApiIntegrationTest {

    @Autowired
    private SeatAvailability seatAvailability;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void locksStatusesReleasesAndRecovers() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "座位可用性测试演出");
        long performanceId = setup.performanceId();
        List<Long> seatIds = setup.seatIds();
        List<Long> firstTwo = seatIds.subList(0, 2);
        String token = "availability-test";
        String otherToken = "other-token";

        assertThat(seatAvailability.statuses(performanceId, firstTwo).values())
                .containsOnly(SeatStatus.AVAILABLE);
        assertThat(seatAvailability.lockAll(performanceId, firstTwo, token)).isTrue();

        Map<Long, SeatStatus> locked = seatAvailability.statuses(performanceId, seatIds);
        assertThat(locked.get(firstTwo.get(0))).isEqualTo(SeatStatus.LOCKED);
        assertThat(locked.get(firstTwo.get(1))).isEqualTo(SeatStatus.LOCKED);
        assertThat(locked.get(seatIds.get(2))).isEqualTo(SeatStatus.AVAILABLE);

        seatAvailability.release(performanceId, firstTwo, otherToken);
        assertThat(seatAvailability.statuses(performanceId, firstTwo).values())
                .containsOnly(SeatStatus.LOCKED);

        seatAvailability.release(performanceId, firstTwo, token);
        assertThat(seatAvailability.statuses(performanceId, firstTwo).values())
                .containsOnly(SeatStatus.AVAILABLE);

        assertThat(seatAvailability.lockAll(performanceId, firstTwo, token)).isTrue();
        redis.delete(redis.keys("seat:" + performanceId + ":*"));
        assertThat(seatAvailability.statuses(performanceId, firstTwo).values())
                .containsOnly(SeatStatus.AVAILABLE);

        seatAvailability.recover(performanceId, firstTwo, token);
        assertThat(seatAvailability.statuses(performanceId, firstTwo).values())
                .containsOnly(SeatStatus.LOCKED);
    }
}
