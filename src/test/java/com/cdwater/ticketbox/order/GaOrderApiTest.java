package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.ApiIntegrationTest;
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
        Setup setup = gaPerformance(admin, "无座下单测试演出", 10);
        String user = userToken("ga_user_a");

        HttpResponse<String> order = createGaOrder(user, setup.performanceId(), setup.tierId(), 2, "ga-key-1");

        assertThat(order.statusCode()).isEqualTo(201);
        assertThat(order.body()).contains("\"quantity\":2").contains("\"totalAmount\":200.00");
        assertThat(stockOf(setup.performanceId())).isEqualTo(8);
    }

    @Test
    void concurrentGaOrdersDoNotOversell() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "无座并发测试演出", 3);
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
        Setup setup = gaPerformance(admin, "无座支付测试演出", 10);
        String user = userToken("ga_pay_user");
        long orderId = idFrom(createGaOrder(user, setup.performanceId(), setup.tierId(), 2, "ga-pay-key").body());

        HttpResponse<String> payment = post("/api/orders/" + orderId + "/payments",
                "{\"attendees\":[{\"name\":\"张三\",\"idNo\":\"310101199001011234\"},"
                        + "{\"name\":\"李四\",\"idNo\":\"310101199202022345\"}]}",
                user);
        assertThat(payment.statusCode()).isEqualTo(201);

        HttpResponse<String> paid = waitForPaid(user, orderId);
        assertThat(paid.body()).contains("\"status\":\"PAID\"");
        assertThat(occurrences(paid.body(), "\"qrPayload\"")).isEqualTo(2);
        assertThat(paid.body()).contains("\"seatId\":null").contains("内场");
        assertThat(paid.body()).contains("\"quantity\":2");
        assertThat(stockOf(setup.performanceId())).isEqualTo(8);
    }

    @Test
    void cancelGaOrderRestoresDbAndRedisStock() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "无座取消测试演出", 2);
        String owner = userToken("ga_cancel_user");
        String other = userToken("ga_cancel_other");
        long orderId = idFrom(createGaOrder(owner, setup.performanceId(), setup.tierId(), 2, "ga-cancel-1").body());
        assertThat(stockOf(setup.performanceId())).isZero();

        HttpResponse<String> cancelled = post("/api/orders/" + orderId + "/cancel", "", owner);

        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(cancelled.body()).contains("CANCELLED");
        assertThat(stockOf(setup.performanceId())).isEqualTo(2);

        // This only succeeds if the Redis counter was restored too; the DB alone is not consulted by the pre-check.
        HttpResponse<String> reorder = createGaOrder(other, setup.performanceId(), setup.tierId(), 2, "ga-cancel-2");
        assertThat(reorder.statusCode()).isEqualTo(201);
        assertThat(stockOf(setup.performanceId())).isZero();
    }

    @Test
    void gaAttendeeMismatchIsRejected() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "无座观演人测试演出", 10);
        String user = userToken("ga_attendee_user");
        long orderId = idFrom(createGaOrder(user, setup.performanceId(), setup.tierId(), 2, "ga-attendee").body());

        HttpResponse<String> payment = post("/api/orders/" + orderId + "/payments",
                "{\"attendees\":[{\"name\":\"只有一人\",\"idNo\":\"310101199001011234\"}]}",
                user);

        assertThat(payment.statusCode()).isEqualTo(400);
        assertThat(payment.body()).contains("ATTENDEE_MISMATCH");
    }

    @Test
    void seatedPerformanceRejectsGeneralAdmissionOrder() throws Exception {
        String admin = adminToken();
        HttpResponse<String> event = post("/api/admin/events",
                "{\"title\":\"有座拒绝无座测试演出\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        long eventId = idFrom(event.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);
        long performanceId = createPerformance(admin, eventId, "有座场馆");
        long tierId = createTier(admin, performanceId, 100, 0);
        post("/api/admin/performances/" + performanceId + "/sections",
                "{\"name\":\"A区\",\"priceTierId\":" + tierId + ",\"rowCount\":1,\"seatsPerRow\":2}",
                admin);
        String user = userToken("ga_on_seated_user");

        HttpResponse<String> order = createGaOrder(user, performanceId, tierId, 1, "ga-on-seated");

        assertThat(order.statusCode()).isEqualTo(400);
        assertThat(order.body()).contains("SEATED_PERFORMANCE");
    }

    @Test
    void gaPerformanceRejectsSeatSelection() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "无座拒绝选座测试演出", 10);
        String user = userToken("seat_on_ga_user");

        HttpResponse<String> order = post("/api/orders",
                "{\"performanceId\":" + setup.performanceId() + ",\"seatIds\":[1]}",
                user,
                Map.of("Idempotency-Key", "seat-on-ga"));

        assertThat(order.statusCode()).isEqualTo(400);
        assertThat(order.body()).contains("NOT_SEATED");
    }

    private Setup gaPerformance(String admin, String title, int stock) throws Exception {
        HttpResponse<String> event = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(event.statusCode()).isEqualTo(201);
        long eventId = idFrom(event.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);
        long performanceId = createPerformance(admin, eventId, "无座场馆");
        long tierId = createTier(admin, performanceId, 100, stock);
        return new Setup(performanceId, tierId);
    }

    private long createPerformance(String admin, long eventId, String venue) throws Exception {
        HttpResponse<String> performance = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"" + venue + "\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}",
                admin);
        assertThat(performance.statusCode()).isEqualTo(201);
        return idFrom(performance.body());
    }

    private long createTier(String admin, long performanceId, int price, int stock) throws Exception {
        HttpResponse<String> tier = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\","
                        + "\"price\":" + price + ",\"stock\":" + stock + "}",
                admin);
        assertThat(tier.statusCode()).isEqualTo(201);
        return idFrom(tier.body());
    }

    private HttpResponse<String> createGaOrder(String user, long performanceId, long tierId,
                                               int quantity, String key) throws Exception {
        return post("/api/orders",
                "{\"performanceId\":" + performanceId + ",\"priceTierId\":" + tierId
                        + ",\"quantity\":" + quantity + "}",
                user,
                Map.of("Idempotency-Key", key));
    }

    private int stockOf(long performanceId) throws Exception {
        String body = get("/api/performances/" + performanceId + "/seat-map", null).body();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"stock\":(\\d+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
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

    private String userToken(String username) throws Exception {
        post("/api/auth/register", "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        return tokenFrom(login.body());
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

    private record Setup(long performanceId, long tierId) {
    }
}
