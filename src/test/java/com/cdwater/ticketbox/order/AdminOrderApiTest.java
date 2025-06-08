package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminOrderApiTest extends ApiIntegrationTest {

    @Test
    void adminCanListAndReadOrders() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "管理订单测试演出", 5);
        String user = userToken("admin_order_user");
        HttpResponse<String> order = createGaOrder(
                user, setup.performanceId(), setup.tierId(), 1, "admin-order-key");
        assertThat(order.statusCode()).isEqualTo(201);
        long orderId = idFrom(order.body());
        String orderNo = jsonField(order.body(), "orderNo");

        HttpResponse<String> list = get("/api/admin/orders", admin);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(orderNo).contains("admin_order_user");

        HttpResponse<String> detail = get("/api/admin/orders/" + orderId, admin);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains(orderNo).contains("admin_order_user");
    }

    @Test
    void adminCanFilterOrdersByStatus() throws Exception {
        String admin = adminToken();
        GaPerformance setup = gaPerformance(admin, "订单筛选测试演出", 5);
        String user = userToken("admin_filter_user");
        HttpResponse<String> order = createGaOrder(
                user, setup.performanceId(), setup.tierId(), 1, "admin-filter-key");
        assertThat(order.statusCode()).isEqualTo(201);
        String orderNo = jsonField(order.body(), "orderNo");

        HttpResponse<String> pending = get("/api/admin/orders?status=PENDING_PAYMENT", admin);
        assertThat(pending.statusCode()).isEqualTo(200);
        assertThat(pending.body()).contains(orderNo);

        HttpResponse<String> paid = get("/api/admin/orders?status=PAID", admin);
        assertThat(paid.statusCode()).isEqualTo(200);
        assertThat(paid.body()).doesNotContain(orderNo);
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
}
