package com.cdwater.ticketbox.waitingroom;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingRoomApiTest extends ApiIntegrationTest {

    @Test
    void rateLimitedPerformanceRequiresAdmission() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "热点准入测试演出", 5, java.time.Instant.now().toString());
        configureWaitingRoom(admin, setup.performanceId(), true, 1, 120);
        String user = userToken("waiting_user");

        HttpResponse<String> blockedOrder = createOrder(user, setup);
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

        HttpResponse<String> order = createOrder(user, setup);
        assertThat(order.statusCode()).isEqualTo(201);
    }

    @Test
    void nonRateLimitedPerformanceSkipsWaitingRoom() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "非热点测试演出", 5);
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
        assertThat(createOrder(user, setup).statusCode()).isEqualTo(201);
    }

    @Test
    void adminCanConfigureWaitingRoom() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "限流配置测试演出", 5);

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
        GaPerformance setup = gaPerformance(admin, "未取号测试演出", 5);
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

    private HttpResponse<String> createOrder(String user, GaPerformance setup) throws Exception {
        return createGaOrder(user, setup.performanceId(), setup.tierId(), 1,
                "waiting-order-" + user.hashCode() + "-" + setup.performanceId());
    }
}
