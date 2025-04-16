package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.order.expiry=2s",
        "app.order.sweep-interval=500"
})
class OrderTimeoutApiTest extends ApiIntegrationTest {

    @Test
    void expiredOrderIsCancelledAndSeatReleased() throws Exception {
        String admin = adminToken();
        long performanceId = seatedPerformance(admin);
        List<Long> seatIds = seatIdsFor(performanceId);
        String user = userToken("timeout_user");

        HttpResponse<String> order = createOrder(user, performanceId, List.of(seatIds.get(0)), "timeout-key");
        assertThat(order.statusCode()).isEqualTo(201);
        long orderId = idFrom(order.body());

        String status = "";
        for (int attempt = 0; attempt < 20; attempt++) {
            Thread.sleep(500);
            HttpResponse<String> current = get("/api/orders/" + orderId, user);
            if (current.body().contains("\"status\":\"CANCELLED\"")) {
                status = "CANCELLED";
                break;
            }
        }

        assertThat(status).isEqualTo("CANCELLED");
        assertThat(lockedSeats(performanceId)).isZero();

        HttpResponse<String> reorder = createOrder(user, performanceId, List.of(seatIds.get(0)), "timeout-key-2");
        assertThat(reorder.statusCode()).isEqualTo(201);
    }

    private long seatedPerformance(String admin) throws Exception {
        HttpResponse<String> created = post("/api/admin/events",
                "{\"title\":\"超时取消测试演出\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(created.statusCode()).isEqualTo(201);
        long eventId = idFrom(created.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);

        HttpResponse<String> performance = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"超时测试场馆\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}",
                admin);
        long performanceId = idFrom(performance.body());

        HttpResponse<String> tier = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\",\"price\":100.00,\"stock\":0}",
                admin);
        long tierId = idFrom(tier.body());
        post("/api/admin/performances/" + performanceId + "/sections",
                "{\"name\":\"A区\",\"priceTierId\":" + tierId + ",\"rowCount\":1,\"seatsPerRow\":2}",
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

    private HttpResponse<String> createOrder(String userToken, long performanceId,
                                             List<Long> seatIds, String idempotencyKey) throws Exception {
        String seats = seatIds.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
        return post("/api/orders",
                "{\"performanceId\":" + performanceId + ",\"seatIds\":[" + seats + "]}",
                userToken,
                Map.of("Idempotency-Key", idempotencyKey));
    }

    private String userToken(String username) throws Exception {
        post("/api/auth/register", "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        return tokenFrom(login.body());
    }

    private int lockedSeats(long performanceId) throws Exception {
        return occurrences(get("/api/performances/" + performanceId + "/seat-map", null).body(),
                "\"status\":\"LOCKED\"");
    }

    private static int occurrences(String body, String token) {
        int count = 0;
        int index = body.indexOf(token);
        while (index >= 0) {
            count++;
            index = body.indexOf(token, index + token.length());
        }
        return count;
    }
}
