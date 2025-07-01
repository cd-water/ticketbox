package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class OrderApiTest extends ApiIntegrationTest {

    @Test
    void createOrderLocksSeats() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "下单锁座测试演出");
        String user = userToken("order_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);

        HttpResponse<String> order = createSeatedOrder(user, setup.performanceId(), chosen, "key-create-1");

        assertThat(order.statusCode()).isEqualTo(201);
        assertThat(order.body())
                .contains("\"status\":\"PENDING_PAYMENT\"")
                .contains("\"totalAmount\":200.00");
        assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isEqualTo(2);

        HttpResponse<String> mine = get("/api/orders", user);
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.body()).contains(jsonField(order.body(), "orderNo"));
    }

    @Test
    void idempotencyKeyReturnsSameOrder() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "幂等测试演出");
        String user = userToken("idempotent_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);

        HttpResponse<String> first = createSeatedOrder(user, setup.performanceId(), chosen, "same-key");
        HttpResponse<String> second = createSeatedOrder(user, setup.performanceId(), chosen, "same-key");

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(idFrom(second.body())).isEqualTo(idFrom(first.body()));
        assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isEqualTo(2);
    }

    @Test
    void concurrentSameIdempotencyKeyReturnsOneOrder() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "并发幂等测试演出");
        String user = userToken("concurrent_idem_user");
        List<Long> firstSeat = List.of(setup.seatIds().get(0));
        List<Long> secondSeat = List.of(setup.seatIds().get(1));

        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        Callable<HttpResponse<String>> attemptA = () -> {
            barrier.await();
            return createSeatedOrder(user, setup.performanceId(), firstSeat, "concurrent-key");
        };
        Callable<HttpResponse<String>> attemptB = () -> {
            barrier.await();
            return createSeatedOrder(user, setup.performanceId(), secondSeat, "concurrent-key");
        };
        try {
            var a = pool.submit(attemptA);
            var b = pool.submit(attemptB);
            HttpResponse<String> responseA = a.get();
            HttpResponse<String> responseB = b.get();

            List<Integer> statuses = List.of(responseA.statusCode(), responseB.statusCode())
                    .stream().sorted().toList();
            assertThat(statuses).containsExactly(200, 201);
            assertThat(idFrom(responseA.body())).isEqualTo(idFrom(responseB.body()));
            assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void purchaseLimitIsEnforced() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "限购测试演出");
        String user = userToken("quota_user");

        HttpResponse<String> four = createSeatedOrder(user, setup.performanceId(),
                setup.seatIds().subList(0, 4), "quota-key-1");
        assertThat(four.statusCode()).isEqualTo(201);

        HttpResponse<String> extra = createSeatedOrder(user, setup.performanceId(),
                setup.seatIds().subList(4, 6), "quota-key-2");

        assertThat(extra.statusCode()).isEqualTo(409);
        assertThat(extra.body()).contains("PURCHASE_LIMIT_EXCEEDED");
        assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isEqualTo(4);
    }

    @Test
    void concurrentSeatSelectionOnlyOneWins() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "并发抢座测试演出");
        String firstUser = userToken("race_user_a");
        String secondUser = userToken("race_user_b");
        List<Long> seat = List.of(setup.seatIds().get(0));

        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        Callable<HttpResponse<String>> attemptA = () -> {
            barrier.await();
            return createSeatedOrder(firstUser, setup.performanceId(), seat, "race-key-a");
        };
        Callable<HttpResponse<String>> attemptB = () -> {
            barrier.await();
            return createSeatedOrder(secondUser, setup.performanceId(), seat, "race-key-b");
        };
        try {
            var a = pool.submit(attemptA);
            var b = pool.submit(attemptB);
            HttpResponse<String> responseA = a.get();
            HttpResponse<String> responseB = b.get();

            List<Integer> statuses = List.of(responseA.statusCode(), responseB.statusCode())
                    .stream().sorted().toList();
            assertThat(statuses).containsExactly(201, 409);
            HttpResponse<String> rejected = responseA.statusCode() == 409 ? responseA : responseB;
            assertThat(rejected.body()).contains("SEAT_ALREADY_LOCKED");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void cancelReleasesSeatAndQuota() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "取消订单测试演出");
        String user = userToken("cancel_user");
        List<Long> chosen = setup.seatIds().subList(0, 2);

        HttpResponse<String> order = createSeatedOrder(user, setup.performanceId(), chosen, "cancel-key-1");
        long orderId = idFrom(order.body());
        assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isEqualTo(2);

        HttpResponse<String> cancelled = post("/api/orders/" + orderId + "/cancel", "", user);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(cancelled.body()).contains("CANCELLED");
        assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isZero();

        HttpResponse<String> reorder = createSeatedOrder(user, setup.performanceId(), chosen, "cancel-key-2");
        assertThat(reorder.statusCode()).isEqualTo(201);
    }

    @Test
    void unknownSeatIsRejected() throws Exception {
        String admin = adminToken();
        SeatedPerformance first = seatedPerformance(admin, "座位归属测试演出");
        SeatedPerformance second = seatedPerformance(admin, "座位归属测试演出二");
        String user = userToken("seat_owner_user");

        HttpResponse<String> order = createSeatedOrder(user, first.performanceId(),
                List.of(second.seatIds().get(0)), "foreign-seat-key");

        assertThat(order.statusCode()).isEqualTo(404);
        assertThat(order.body()).contains("SEAT_NOT_FOUND");
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "幂等头测试演出");
        String user = userToken("no_key_user");

        HttpResponse<String> order = post("/api/orders",
                "{\"performanceId\":" + setup.performanceId()
                        + ",\"seatIds\":[" + setup.seatIds().get(0) + "]}",
                user);

        assertThat(order.statusCode()).isEqualTo(400);
        assertThat(order.body()).contains("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void cannotReadAnotherUsersOrder() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "订单归属测试演出");
        String owner = userToken("order_owner");
        String stranger = userToken("order_stranger");
        long orderId = idFrom(createSeatedOrder(owner, setup.performanceId(),
                List.of(setup.seatIds().get(0)), "owner-key").body());

        HttpResponse<String> response = get("/api/orders/" + orderId, stranger);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("ORDER_NOT_FOUND");
    }
}
