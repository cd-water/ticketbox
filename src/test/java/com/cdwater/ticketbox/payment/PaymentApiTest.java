package com.cdwater.ticketbox.payment;

import com.cdwater.ticketbox.ApiIntegrationTest;
import com.cdwater.ticketbox.common.HmacSigner;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
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
        SeatedPerformance setup = seatedPerformance(admin, "支付出票测试演出");
        String user = userToken("paying_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = idFrom(createSeatedOrder(user, setup.performanceId(), chosen, "pay-key-1").body());

        HttpResponse<String> payment = payOrder(user, orderId, attendees(chosen, "张三", "李四"));
        assertThat(payment.statusCode()).isEqualTo(201);
        assertThat(payment.body()).contains("PENDING");

        HttpResponse<String> paid = waitForOrderStatus(user, orderId, "PAID");
        assertThat(paid.body()).contains("\"status\":\"PAID\"");
        assertThat(occurrences(paid.body(), "\"qrPayload\"")).isEqualTo(2);
        assertThat(paid.body()).contains("张三").contains("李四");

        assertThat(seatStatusCount(setup.performanceId(), "SOLD")).isEqualTo(2);
        assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isZero();
    }

    @Test
    void callbackIsIdempotent() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "回调幂等测试演出");
        String user = userToken("callback_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = idFrom(createSeatedOrder(user, setup.performanceId(), chosen, "callback-key-1").body());

        HttpResponse<String> payment = payOrder(user, orderId, attendees(chosen, "王五", "赵六"));
        String providerTxnNo = jsonField(payment.body(), "providerTxnNo");
        String amount = amountFrom(payment.body());
        waitForOrderStatus(user, orderId, "PAID");

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
        SeatedPerformance setup = seatedPerformance(admin, "并发回调测试演出");
        String user = userToken("concurrent_callback_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = idFrom(createSeatedOrder(
                user, setup.performanceId(), chosen, "concurrent-callback-key").body());

        HttpResponse<String> payment = payOrder(user, orderId, attendees(chosen, "张三", "李四"));
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

        HttpResponse<String> paid = waitForOrderStatus(user, orderId, "PAID");
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
        SeatedPerformance setup = seatedPerformance(admin, "观演人校验测试演出");
        String user = userToken("attendee_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);
        long orderId = idFrom(createSeatedOrder(user, setup.performanceId(), chosen, "attendee-key-1").body());

        HttpResponse<String> payment = payOrder(user, orderId, attendees(chosen.subList(0, 1), "只有一人"));

        assertThat(payment.statusCode()).isEqualTo(400);
        assertThat(payment.body()).contains("ATTENDEE_MISMATCH");
    }

    @Test
    void cancelledOrderCannotBePaid() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "已取消支付测试演出");
        String user = userToken("cancelled_pay_user");
        List<Long> chosen = setup.seatIds().subList(0, 1);
        long orderId = idFrom(createSeatedOrder(user, setup.performanceId(), chosen, "cancelled-pay-key").body());
        post("/api/orders/" + orderId + "/cancel", "", user);

        HttpResponse<String> payment = payOrder(user, orderId, attendees(chosen, "张三"));

        assertThat(payment.statusCode()).isEqualTo(409);
        assertThat(payment.body()).contains("ORDER_NOT_PAYABLE");
    }

    @Test
    void cannotPayAnotherUsersOrder() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "支付归属测试演出");
        String owner = userToken("pay_owner");
        String stranger = userToken("pay_stranger");
        List<Long> chosen = setup.seatIds().subList(0, 1);
        long orderId = idFrom(createSeatedOrder(owner, setup.performanceId(), chosen, "owner-pay-key").body());

        HttpResponse<String> payment = payOrder(stranger, orderId, attendees(chosen, "张三"));

        assertThat(payment.statusCode()).isEqualTo(404);
        assertThat(payment.body()).contains("ORDER_NOT_FOUND");
    }

    @Test
    void paidOrderCannotBePaidAgain() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "重复支付测试演出");
        String user = userToken("repeat_pay_user");
        List<Long> chosen = setup.seatIds().subList(0, 1);
        long orderId = idFrom(createSeatedOrder(user, setup.performanceId(), chosen, "repeat-pay-key").body());
        payOrder(user, orderId, attendees(chosen, "张三"));
        waitForOrderStatus(user, orderId, "PAID");

        HttpResponse<String> again = payOrder(user, orderId, attendees(chosen, "张三"));

        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(again.body()).contains("ORDER_NOT_PAYABLE");
    }

    private HttpResponse<String> callback(String providerTxnNo, String amount, String signature) throws Exception {
        return post("/api/payments/callback",
                "{\"providerTxnNo\":\"" + providerTxnNo + "\",\"status\":\"SUCCESS\","
                        + "\"amount\":\"" + amount + "\"}",
                null,
                Map.of("X-Mock-Signature", signature));
    }

    private static String amountFrom(String body) {
        Matcher matcher = Pattern.compile("\"amount\"\\s*:\\s*([0-9.]+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
