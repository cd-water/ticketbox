package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class GaOrderApiTest extends ApiIntegrationTest {

    @Test
    void gaOrderDecrementsStock() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "无座下单测试演出", 10);
        String user = userToken("ga_user_a");

        HttpResponse<String> order = createGaOrder(
                user, setup.performanceId(), setup.tierId(), 2, "ga-key-1");

        assertThat(order.statusCode()).isEqualTo(201);
        assertThat(order.body()).contains("\"quantity\":2").contains("\"totalAmount\":200.00");
        assertThat(stockOf(setup.performanceId())).isEqualTo(8);
    }

    @Test
    void concurrentGaOrdersDoNotOversell() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "无座并发测试演出", 3);
        String firstUser = userToken("ga_race_a");
        String secondUser = userToken("ga_race_b");

        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        Callable<HttpResponse<String>> first = () -> {
            barrier.await();
            return createGaOrder(firstUser, setup.performanceId(), setup.tierId(), 2, "ga-race-key-a");
        };
        Callable<HttpResponse<String>> second = () -> {
            barrier.await();
            return createGaOrder(secondUser, setup.performanceId(), setup.tierId(), 2, "ga-race-key-b");
        };
        try {
            var a = pool.submit(first);
            var b = pool.submit(second);
            HttpResponse<String> responseA = a.get();
            HttpResponse<String> responseB = b.get();

            List<Integer> statuses = List.of(responseA.statusCode(), responseB.statusCode())
                    .stream().sorted().toList();
            assertThat(statuses).containsExactly(201, 409);
            HttpResponse<String> rejected = responseA.statusCode() == 409 ? responseA : responseB;
            assertThat(rejected.body()).contains("SOLD_OUT");
            assertThat(stockOf(setup.performanceId())).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void gaPaymentIssuesTickets() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "无座支付测试演出", 10);
        String user = userToken("ga_pay_user");
        long orderId = idFrom(createGaOrder(
                user, setup.performanceId(), setup.tierId(), 2, "ga-pay-key").body());

        HttpResponse<String> payment = payOrder(user, orderId, gaAttendees("张三", "李四"));
        assertThat(payment.statusCode()).isEqualTo(201);

        HttpResponse<String> paid = waitForOrderStatus(user, orderId, "PAID");
        assertThat(paid.body()).contains("\"status\":\"PAID\"");
        assertThat(occurrences(paid.body(), "\"qrPayload\"")).isEqualTo(2);
        assertThat(paid.body()).contains("\"seatId\":null").contains("内场");
        assertThat(paid.body()).contains("\"quantity\":2");
        assertThat(stockOf(setup.performanceId())).isEqualTo(8);
    }

    @Test
    void cancelGaOrderRestoresDbAndRedisStock() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "无座取消测试演出", 2);
        String owner = userToken("ga_cancel_user");
        String other = userToken("ga_cancel_other");
        long orderId = idFrom(createGaOrder(
                owner, setup.performanceId(), setup.tierId(), 2, "ga-cancel-1").body());
        assertThat(stockOf(setup.performanceId())).isZero();

        HttpResponse<String> cancelled = post("/api/orders/" + orderId + "/cancel", "", owner);

        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(cancelled.body()).contains("CANCELLED");
        assertThat(stockOf(setup.performanceId())).isEqualTo(2);

        // This only succeeds if the Redis counter was restored too; the DB alone is not consulted by the pre-check.
        HttpResponse<String> reorder = createGaOrder(
                other, setup.performanceId(), setup.tierId(), 2, "ga-cancel-2");
        assertThat(reorder.statusCode()).isEqualTo(201);
        assertThat(stockOf(setup.performanceId())).isZero();
    }

    @Test
    void gaAttendeeMismatchIsRejected() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "无座观演人测试演出", 10);
        String user = userToken("ga_attendee_user");
        long orderId = idFrom(createGaOrder(
                user, setup.performanceId(), setup.tierId(), 2, "ga-attendee").body());

        HttpResponse<String> payment = payOrder(user, orderId, gaAttendees("只有一人"));

        assertThat(payment.statusCode()).isEqualTo(400);
        assertThat(payment.body()).contains("ATTENDEE_MISMATCH");
    }

    @Test
    void seatedPerformanceRejectsGeneralAdmissionOrder() throws Exception {
        String admin = adminToken();
        SeatedPerformance seated = seatedPerformance(admin, "有座拒绝无座测试演出");
        String user = userToken("ga_on_seated_user");

        HttpResponse<String> order = createGaOrder(
                user, seated.performanceId(), seated.tierId(), 1, "ga-on-seated");

        assertThat(order.statusCode()).isEqualTo(400);
        assertThat(order.body()).contains("SEATED_PERFORMANCE");
    }

    @Test
    void gaPerformanceRejectsSeatSelection() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "无座拒绝选座测试演出", 10);
        String user = userToken("seat_on_ga_user");

        HttpResponse<String> order = post("/api/orders",
                "{\"performanceId\":" + setup.performanceId() + ",\"seatIds\":[1]}",
                user,
                Map.of("Idempotency-Key", "seat-on-ga"));

        assertThat(order.statusCode()).isEqualTo(400);
        assertThat(order.body()).contains("NOT_SEATED");
    }
}
