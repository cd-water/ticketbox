package com.cdwater.ticketbox.ticket;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import com.cdwater.ticketbox.common.HmacSigner;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCheckInApiTest extends ApiIntegrationTest {

    private static final String QR_SECRET = "test-qr-secret";

    @Test
    void validTicketIsCheckedInOnce() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "核销测试演出", 5);
        String user = userToken("checkin_user");
        long orderId = idFrom(createGaOrder(
                user, setup.performanceId(), setup.tierId(), 1, "checkin-" + user.hashCode()).body());
        String qrPayload = payAndGetQr(user, orderId);

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
        GaPerformance setup = gaPerformance(admin, "核销测试演出", 5);
        String user = userToken("tampered_user");
        long orderId = idFrom(createGaOrder(
                user, setup.performanceId(), setup.tierId(), 1, "checkin-" + user.hashCode()).body());
        String qrPayload = payAndGetQr(user, orderId);
        char last = qrPayload.charAt(qrPayload.length() - 1);
        String tampered = qrPayload.substring(0, qrPayload.length() - 1) + (last == 'a' ? 'b' : 'a');

        HttpResponse<String> response = checkIn(admin, tampered);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_TICKET");
    }

    @Test
    void validSignatureForUnknownTicketIsRejected() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "核销测试演出", 5);
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
        GaPerformance setup = gaPerformance(admin, "核销测试演出", 5);
        String user = userToken("manual_checkin_user");
        long orderId = idFrom(createGaOrder(
                user, setup.performanceId(), setup.tierId(), 1, "checkin-" + user.hashCode()).body());
        String qrPayload = payAndGetQr(user, orderId);
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

    private String payAndGetQr(String user, long orderId) throws Exception {
        HttpResponse<String> payment = payOrder(user, orderId, gaAttendees("张三"));
        assertThat(payment.statusCode()).isEqualTo(201);

        HttpResponse<String> order = waitForOrderStatus(user, orderId, "PAID");
        assertThat(order).isNotNull();
        Matcher matcher = Pattern.compile("\"qrPayload\":\"([^\"]+)\"").matcher(order.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
