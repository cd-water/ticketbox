package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminOrderApiTest extends ApiIntegrationTest {

    @Test
    void adminCanListAndReadOrders() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "管理订单测试演出");
        String user = userToken("admin_order_user");
        CreatedOrder created = createOrder(user, setup, "admin-order-key");

        HttpResponse<String> list = get("/api/admin/orders", admin);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(created.orderNo()).contains("admin_order_user");

        HttpResponse<String> detail = get("/api/admin/orders/" + created.id(), admin);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains(created.orderNo()).contains("admin_order_user");
    }

    @Test
    void adminCanFilterOrdersByStatus() throws Exception {
        String admin = adminToken();
        Setup setup = gaPerformance(admin, "订单筛选测试演出");
        String user = userToken("admin_filter_user");
        CreatedOrder created = createOrder(user, setup, "admin-filter-key");

        HttpResponse<String> pending = get("/api/admin/orders?status=PENDING_PAYMENT", admin);
        assertThat(pending.statusCode()).isEqualTo(200);
        assertThat(pending.body()).contains(created.orderNo());

        HttpResponse<String> paid = get("/api/admin/orders?status=PAID", admin);
        assertThat(paid.statusCode()).isEqualTo(200);
        assertThat(paid.body()).doesNotContain(created.orderNo());
    }

    @Test
    void regularUserCannotListOrders() throws Exception {
        String user = userToken("forbidden_admin_order_user");

        assertThat(get("/api/admin/orders", user).statusCode()).isEqualTo(403);
    }

    @Test
    void unknownAdminOrderIsRejected() throws Exception {
        HttpResponse<String> response = get("/api/admin/orders/999999", adminToken());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("ORDER_NOT_FOUND");
    }

    private Setup gaPerformance(String admin, String title) throws Exception {
        HttpResponse<String> event = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(event.statusCode()).isEqualTo(201);
        long eventId = idFrom(event.body());
        post("/api/admin/events/" + eventId + "/publish", "", admin);

        HttpResponse<String> performance = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"管理订单场馆\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}",
                admin);
        assertThat(performance.statusCode()).isEqualTo(201);
        long performanceId = idFrom(performance.body());

        HttpResponse<String> tier = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\",\"price\":100.00,\"stock\":5}",
                admin);
        assertThat(tier.statusCode()).isEqualTo(201);
        return new Setup(performanceId, idFrom(tier.body()));
    }

    private CreatedOrder createOrder(String user, Setup setup, String key) throws Exception {
        HttpResponse<String> order = post("/api/orders",
                "{\"performanceId\":" + setup.performanceId() + ",\"priceTierId\":"
                        + setup.tierId() + ",\"quantity\":1}",
                user,
                Map.of("Idempotency-Key", key));
        assertThat(order.statusCode()).isEqualTo(201);
        return new CreatedOrder(idFrom(order.body()), jsonField(order.body(), "orderNo"));
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

    private record CreatedOrder(long id, String orderNo) {
    }
}
