package com.cdwater.ticketbox.ticket;

import com.cdwater.ticketbox.ApiIntegrationTest;
import com.cdwater.ticketbox.common.HmacSigner;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCheckInApiTest extends ApiIntegrationTest {

    private static final String QR_SECRET = "test-qr-secret";

    @Test
    void validTicketIsCheckedInOnce() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin);
        String user = userToken("checkin_user");
        long orderId = orderId(user, setup.performanceId(), setup.tierId());
        String qrPayload = payAndGetQr(user, orderId, setup.tierId());

        HttpResponse<String> first = checkIn(admin, qrPayload);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body())
                .contains("\"status\":\"CHECKED_IN\"")
                .contains("张三")
                .contains("内场")
                .contains("\"rowNo\":null");

        HttpResponse<String> second = checkIn(admin, qrPayload);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("ALREADY_CHECKED_IN");
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin);
        String user = userToken("tampered_user");
        long orderId = orderId(user, setup.performanceId(), setup.tierId());
        String qrPayload = payAndGetQr(user, orderId, setup.tierId());
        char last = qrPayload.charAt(qrPayload.length() - 1);
        String tampered = qrPayload.substring(0, qrPayload.length() - 1) + (last == 'a' ? 'b' : 'a');

        HttpResponse<String> response = checkIn(admin, tampered);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_TICKET");
    }

    @Test
    void validSignatureForUnknownTicketIsRejected() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin);
        long issuedAt = Instant.now().getEpochSecond();
        String canonical = "999999|" + setup.performanceId() + "|" + issuedAt;
        String payload = "999999." + setup.performanceId() + "." + issuedAt
                + "." + HmacSigner.sign(QR_SECRET, canonical);

        HttpResponse<String> response = checkIn(admin, payload);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_TICKET");
    }

    @Test
    void manualTicketNumberIsAcceptedAsFallback() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin);
        String user = userToken("manual_checkin_user");
        long orderId = orderId(user, setup.performanceId(), setup.tierId());
        String qrPayload = payAndGetQr(user, orderId, setup.tierId());
        String ticketNumber = qrPayload.split("\\.")[0];

        HttpResponse<String> first = checkIn(admin, ticketNumber);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"status\":\"CHECKED_IN\"");

        HttpResponse<String> second = checkIn(admin, ticketNumber);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("ALREADY_CHECKED_IN");
    }

    @Test
    void blankPayloadIsInvalidTicket() throws Exception {
        String admin = adminToken();

        HttpResponse<String> response = checkIn(admin, "   ");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_TICKET");
    }

    @Test
    void regularUserCannotCheckIn() throws Exception {
        String user = userToken("checkin_forbidden_user");

        HttpResponse<String> response = checkIn(user, "1.1.1.deadbeef");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("FORBIDDEN");
    }

    private HttpResponse<String> checkIn(String token, String payload) throws Exception {
        return post("/api/admin/tickets/check-in", "{\"payload\":\"" + payload + "\"}", token);
    }

    private Setup gaPerformance(String admin) throws Exception {
        HttpResponse<String> event = post("/api/admin/events",
                "{\"title\":\"核销测试演出\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(event.statusCode()).isEqualTo(201);
        long eventId = idFrom(event.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);

        HttpResponse<String> performance = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"核销测试场馆\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}",
                admin);
        long performanceId = idFrom(performance.body());

        HttpResponse<String> tier = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\",\"price\":100.00,\"stock\":5}",
                admin);
        assertThat(tier.statusCode()).isEqualTo(201);
        return new Setup(performanceId, idFrom(tier.body()));
    }

    private long orderId(String user, long performanceId, long tierId) throws Exception {
        HttpResponse<String> order = post("/api/orders",
                "{\"performanceId\":" + performanceId + ",\"priceTierId\":" + tierId + ",\"quantity\":1}",
                user,
                Map.of("Idempotency-Key", "checkin-" + user.hashCode()));
        assertThat(order.statusCode()).as(order.body()).isEqualTo(201);
        return idFrom(order.body());
    }

    private String payAndGetQr(String user, long orderId, long tierId) throws Exception {
        HttpResponse<String> payment = post("/api/orders/" + orderId + "/payments",
                "{\"attendees\":[{\"name\":\"张三\",\"idNo\":\"310101199001011234\"}]}",
                user);
        assertThat(payment.statusCode()).isEqualTo(201);

        HttpResponse<String> order = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            Thread.sleep(100);
            order = get("/api/orders/" + orderId, user);
            if (order.body().contains("\"status\":\"PAID\"")) {
                break;
            }
        }
        assertThat(order).isNotNull();
        Matcher matcher = Pattern.compile("\"qrPayload\":\"([^\"]+)\"").matcher(order.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String userToken(String username) throws Exception {
        post("/api/auth/register", "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        return tokenFrom(login.body());
    }

    private record Setup(long performanceId, long tierId) {
    }
}
