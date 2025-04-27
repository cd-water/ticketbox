package com.cdwater.ticketbox.waitingroom;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingRoomApiTest extends ApiIntegrationTest {

    @Test
    void rateLimitedPerformanceRequiresAdmission() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "热点准入测试演出", java.time.Instant.now().toString());
        configureWaitingRoom(admin, setup.performanceId(), true, 1, 120);
        String user = userToken("waiting_user");

        HttpResponse<String> blockedOrder = createGaOrder(user, setup);
        assertThat(blockedOrder.statusCode()).isEqualTo(409);
        assertThat(blockedOrder.body()).contains("TOKEN_REQUIRED");

        HttpResponse<String> anonymousSeatMap = get(
                "/api/performances/" + setup.performanceId() + "/seat-map", null);
        assertThat(anonymousSeatMap.statusCode()).isEqualTo(409);
        assertThat(anonymousSeatMap.body()).contains("TOKEN_REQUIRED");

        HttpResponse<String> joined = post(
                "/api/performances/" + setup.performanceId() + "/waiting-room/join", "", user);
        assertThat(joined.statusCode()).isEqualTo(200);
        assertThat(joined.body()).contains("\"joined\":true");

        String status = pollUntilAdmitted(user, setup.performanceId());
        assertThat(status).contains("\"admitted\":true");

        HttpResponse<String> seatMap = get(
                "/api/performances/" + setup.performanceId() + "/seat-map", user);
        assertThat(seatMap.statusCode()).isEqualTo(200);

        HttpResponse<String> order = createGaOrder(user, setup);
        assertThat(order.statusCode()).isEqualTo(201);
    }

    @Test
    void nonRateLimitedPerformanceSkipsWaitingRoom() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "非热点测试演出");
        String user = userToken("no_queue_user");

        HttpResponse<String> join = post(
                "/api/performances/" + setup.performanceId() + "/waiting-room/join", "", user);
        assertThat(join.statusCode()).isEqualTo(400);
        assertThat(join.body()).contains("WAITING_ROOM_DISABLED");

        HttpResponse<String> status = get(
                "/api/performances/" + setup.performanceId() + "/waiting-room/status", user);
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).contains("\"enabled\":false");

        assertThat(get("/api/performances/" + setup.performanceId() + "/seat-map", null).statusCode())
                .isEqualTo(200);
        assertThat(createGaOrder(user, setup).statusCode()).isEqualTo(201);
    }

    @Test
    void adminCanConfigureWaitingRoom() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "限流配置测试演出");

        HttpResponse<String> configured = configureWaitingRoom(admin, setup.performanceId(), true, 5, 180);

        assertThat(configured.statusCode()).isEqualTo(200);
        assertThat(configured.body())
                .contains("\"waitingRoomEnabled\":true")
                .contains("\"admissionRate\":5")
                .contains("\"tokenTtlSeconds\":180");

        HttpResponse<String> listed = get(
                "/api/admin/performances?eventId=" + setup.eventId(), admin);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body())
                .contains("\"waitingRoomEnabled\":true")
                .contains("\"admissionRate\":5")
                .contains("\"tokenTtlSeconds\":180");
    }

    @Test
    void statusReportsNotJoinedBeforeJoin() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "未取号测试演出");
        configureWaitingRoom(admin, setup.performanceId(), true, 1, 120);
        String user = userToken("not_joined_user");

        HttpResponse<String> status = get(
                "/api/performances/" + setup.performanceId() + "/waiting-room/status", user);

        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).contains("\"enabled\":true").contains("\"joined\":false");
    }

    private String pollUntilAdmitted(String user, long performanceId) throws Exception {
        String body = "";
        for (int attempt = 0; attempt < 50; attempt++) {
            Thread.sleep(100);
            HttpResponse<String> status = get(
                    "/api/performances/" + performanceId + "/waiting-room/status", user);
            body = status.body();
            if (body.contains("\"admitted\":true")) {
                return body;
            }
        }
        return body;
    }

    private HttpResponse<String> configureWaitingRoom(String admin, long performanceId,
                                                      boolean enabled, int rate, int ttl) throws Exception {
        return put("/api/admin/performances/" + performanceId + "/waiting-room",
                "{\"enabled\":" + enabled + ",\"admissionRate\":" + rate
                        + ",\"tokenTtlSeconds\":" + ttl + "}",
                admin);
    }

    private Setup gaPerformance(String admin, String title) throws Exception {
        return gaPerformance(admin, title, "2026-11-20T02:00:00Z");
    }

    private Setup gaPerformance(String admin, String title, String saleStartAt) throws Exception {
        HttpResponse<String> event = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(event.statusCode()).isEqualTo(201);
        long eventId = idFrom(event.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);

        HttpResponse<String> performance = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"等待室测试场馆\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"" + saleStartAt + "\"}",
                admin);
        long performanceId = idFrom(performance.body());

        HttpResponse<String> tier = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\",\"price\":100.00,\"stock\":5}",
                admin);
        assertThat(tier.statusCode()).isEqualTo(201);
        return new Setup(performanceId, idFrom(tier.body()), eventId);
    }

    private HttpResponse<String> createGaOrder(String user, Setup setup) throws Exception {
        return post("/api/orders",
                "{\"performanceId\":" + setup.performanceId() + ",\"priceTierId\":"
                        + setup.tierId() + ",\"quantity\":1}",
                user,
                Map.of("Idempotency-Key", "waiting-order-" + user.hashCode()
                        + "-" + setup.performanceId()));
    }

    private String userToken(String username) throws Exception {
        post("/api/auth/register", "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        return tokenFrom(login.body());
    }

    private record Setup(long performanceId, long tierId, long eventId) {
    }
}
