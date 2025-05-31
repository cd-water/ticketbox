package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SeatAvailabilityTest extends ApiIntegrationTest {

    @Autowired
    private SeatAvailability seatAvailability;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void locksStatusesReleasesAndRecovers() throws Exception {
        String admin = adminToken();
        long performanceId = seatedPerformance(admin);
        List<Long> seatIds = seatIdsFor(performanceId);
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

    private long seatedPerformance(String admin) throws Exception {
        HttpResponse<String> event = post("/api/admin/events",
                "{\"title\":\"座位可用性测试演出\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(event.statusCode()).isEqualTo(201);
        long eventId = idFrom(event.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);

        HttpResponse<String> performance = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"可用性场馆\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}",
                admin);
        long performanceId = idFrom(performance.body());

        HttpResponse<String> tier = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\",\"price\":100.00,\"stock\":0}",
                admin);
        long tierId = idFrom(tier.body());
        post("/api/admin/performances/" + performanceId + "/sections",
                "{\"name\":\"A区\",\"priceTierId\":" + tierId + ",\"rowCount\":2,\"seatsPerRow\":3}",
                admin);
        return performanceId;
    }

    private List<Long> seatIdsFor(long performanceId) throws Exception {
        String body = get("/api/performances/" + performanceId + "/seat-map", null).body();
        int start = body.indexOf("\"seats\":[");
        int end = body.indexOf(']', start);
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(body.substring(start, end));
        List<Long> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }
}
