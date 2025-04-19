package com.cdwater.ticketbox.payment;

import com.cdwater.ticketbox.ApiIntegrationTest;
import com.cdwater.ticketbox.common.HmacSigner;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentApiTest extends ApiIntegrationTest {

    private static final String CALLBACK_SECRET = "test-callback-secret";

    @Test
    void paymentIssuesTicketsAndMarksSeatsSold() throws Exception {
        String admin = adminToken();
        Setup setup = seatedPerformance(admin, "支付出票测试演出");
        String user = userToken("paying_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = orderId(user, setup.performanceId(), chosen, "pay-key-1");

        HttpResponse<String> payment = pay(user, orderId, attendees(chosen, "张三", "李四"));
        assertThat(payment.statusCode()).isEqualTo(201);
        assertThat(payment.body()).contains("PENDING");

        HttpResponse<String> paid = waitForPaid(user, orderId);
        assertThat(paid.body()).contains("\"status\":\"PAID\"");
        assertThat(occurrences(paid.body(), "\"qrPayload\"")).isEqualTo(2);
        assertThat(paid.body()).contains("张三").contains("李四");

        String seatMap = get("/api/performances/" + setup.performanceId() + "/seat-map", null).body();
        assertThat(occurrences(seatMap, "\"status\":\"SOLD\"")).isEqualTo(2);
        assertThat(occurrences(seatMap, "\"status\":\"LOCKED\"")).isZero();
    }

    @Test
    void callbackIsIdempotent() throws Exception {
        String admin = adminToken();
        Setup setup = seatedPerformance(admin, "回调幂等测试演出");
        String user = userToken("callback_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = orderId(user, setup.performanceId(), chosen, "callback-key-1");

        HttpResponse<String> payment = pay(user, orderId, attendees(chosen, "王五", "赵六"));
        String providerTxnNo = jsonField(payment.body(), "providerTxnNo");
        String amount = amountFrom(payment.body());
        waitForPaid(user, orderId);

        for (int attempt = 0; attempt < 2; attempt++) {
            String canonical = providerTxnNo + "|SUCCESS|" + amount;
            HttpResponse<String> replay = callback(providerTxnNo, amount,
                    HmacSigner.sign(CALLBACK_SECRET, canonical));
            assertThat(replay.statusCode()).isEqualTo(200);
        }

        HttpResponse<String> order = get("/api/orders/" + orderId, user);
        assertThat(order.body()).contains("\"status\":\"PAID\"");
        assertThat(occurrences(order.body(), "\"qrPayload\"")).isEqualTo(2);
    }

    @Test
    void concurrentCallbacksIssueOneSetOfTickets() throws Exception {
        String admin = adminToken();
        Setup setup = seatedPerformance(admin, "并发回调测试演出");
        String user = userToken("concurrent_callback_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = orderId(user, setup.performanceId(), chosen, "concurrent-callback-key");

        HttpResponse<String> payment = pay(user, orderId, attendees(chosen, "张三", "李四"));
        String providerTxnNo = jsonField(payment.body(), "providerTxnNo");
        String amount = amountFrom(payment.body());

        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        Callable<HttpResponse<String>> attempt = () -> {
            barrier.await();
            String canonical = providerTxnNo + "|SUCCESS|" + amount;
            return callback(providerTxnNo, amount, HmacSigner.sign(CALLBACK_SECRET, canonical));
        };
        try {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(first.get().statusCode()).isEqualTo(200);
            assertThat(second.get().statusCode()).isEqualTo(200);
        } finally {
            pool.shutdown();
        }

        HttpResponse<String> paid = waitForPaid(user, orderId);
        assertThat(paid.body()).contains("\"status\":\"PAID\"");
        assertThat(occurrences(paid.body(), "\"qrPayload\"")).isEqualTo(2);
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/payments/callback",
                "{\"providerTxnNo\":\"MOCK-UNKNOWN\",\"status\":\"SUCCESS\",\"amount\":\"1.00\"}",
                null,
                Map.of("X-Mock-Signature", "deadbeef"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("INVALID_SIGNATURE");
    }

    @Test
    void attendeeMismatchIsRejected() throws Exception {
        String admin = adminToken();
        Setup setup = seatedPerformance(admin, "观演人校验测试演出");
        String user = userToken("attendee_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = orderId(user, setup.performanceId(), chosen, "attendee-key-1");

        HttpResponse<String> payment = pay(user, orderId, attendees(chosen.subList(0, 1), "只有一人"));

        assertThat(payment.statusCode()).isEqualTo(400);
        assertThat(payment.body()).contains("ATTENDEE_MISMATCH");
    }

    @Test
    void cancelledOrderCannotBePaid() throws Exception {
        String admin = adminToken();
        Setup setup = seatedPerformance(admin, "已取消支付测试演出");
        String user = userToken("cancelled_pay_user");
        List<Long> chosen = setup.seatIds().subList(0, 1);
        long orderId = orderId(user, setup.performanceId(), chosen, "cancelled-pay-key");
        post("/api/orders/" + orderId + "/cancel", "", user);

        HttpResponse<String> payment = pay(user, orderId, attendees(chosen, "张三"));

        assertThat(payment.statusCode()).isEqualTo(409);
        assertThat(payment.body()).contains("ORDER_NOT_PAYABLE");
    }

    @Test
    void cannotPayAnotherUsersOrder() throws Exception {
        String admin = adminToken();
        Setup setup = seatedPerformance(admin, "支付归属测试演出");
        String owner = userToken("pay_owner");
        String stranger = userToken("pay_stranger");
        List<Long> chosen = setup.seatIds().subList(0, 1);
        long orderId = orderId(owner, setup.performanceId(), chosen, "owner-pay-key");

        HttpResponse<String> payment = pay(stranger, orderId, attendees(chosen, "张三"));

        assertThat(payment.statusCode()).isEqualTo(404);
        assertThat(payment.body()).contains("ORDER_NOT_FOUND");
    }

    @Test
    void paidOrderCannotBePaidAgain() throws Exception {
        String admin = adminToken();
        Setup setup = seatedPerformance(admin, "重复支付测试演出");
        String user = userToken("repeat_pay_user");
        List<Long> chosen = setup.seatIds().subList(0, 1);
        long orderId = orderId(user, setup.performanceId(), chosen, "repeat-pay-key");
        pay(user, orderId, attendees(chosen, "张三"));
        waitForPaid(user, orderId);

        HttpResponse<String> again = pay(user, orderId, attendees(chosen, "张三"));

        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(again.body()).contains("ORDER_NOT_PAYABLE");
    }

    private HttpResponse<String> waitForPaid(String user, long orderId) throws Exception {
        HttpResponse<String> order = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            Thread.sleep(100);
            order = get("/api/orders/" + orderId, user);
            if (order.body().contains("\"status\":\"PAID\"")) {
                return order;
            }
        }
        return order;
    }

    private HttpResponse<String> callback(String providerTxnNo, String amount, String signature) throws Exception {
        return post("/api/payments/callback",
                "{\"providerTxnNo\":\"" + providerTxnNo + "\",\"status\":\"SUCCESS\","
                        + "\"amount\":\"" + amount + "\"}",
                null,
                Map.of("X-Mock-Signature", signature));
    }

    private HttpResponse<String> pay(String user, long orderId, String attendeesJson) throws Exception {
        return post("/api/orders/" + orderId + "/payments",
                "{\"attendees\":" + attendeesJson + "}", user);
    }

    private long orderId(String user, long performanceId, List<Long> seatIds, String key) throws Exception {
        String seats = seatIds.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
        HttpResponse<String> order = post("/api/orders",
                "{\"performanceId\":" + performanceId + ",\"seatIds\":[" + seats + "]}",
                user,
                Map.of("Idempotency-Key", key));
        assertThat(order.statusCode()).isEqualTo(201);
        return idFrom(order.body());
    }

    private static String attendees(List<Long> seatIds, String... names) {
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < seatIds.size(); index++) {
            String name = names.length == 1 ? names[0] : names[index];
            entries.add("{\"seatId\":" + seatIds.get(index) + ",\"name\":\"" + name
                    + "\",\"idNo\":\"31010119900101123" + index + "\"}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    private Setup seatedPerformance(String admin, String title) throws Exception {
        HttpResponse<String> created = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(created.statusCode()).isEqualTo(201);
        long eventId = idFrom(created.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);

        HttpResponse<String> performance = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"支付测试场馆\","
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

        return new Setup(performanceId, seatIdsFor(performanceId));
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

    private String userToken(String username) throws Exception {
        post("/api/auth/register", "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        return tokenFrom(login.body());
    }

    private static String amountFrom(String body) {
        Matcher matcher = Pattern.compile("\"amount\"\\s*:\\s*([0-9.]+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
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

    private record Setup(long performanceId, List<Long> seatIds) {
    }
}
