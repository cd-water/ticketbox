package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.order.expiry=2s",
        "app.order.sweep-interval=500"
})
class OrderTimeoutApiTest extends ApiIntegrationTest {

    @Test
    void expiredOrderIsCancelledAndSeatReleased() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "超时取消测试演出");
        String user = userToken("timeout_user");
        List<Long> chosen = List.of(setup.seatIds().get(0));

        HttpResponse<String> order = createSeatedOrder(user, setup.performanceId(), chosen, "timeout-key");
        assertThat(order.statusCode()).isEqualTo(201);
        long orderId = idFrom(order.body());

        String status = "";
        for (int attempt = 0; attempt < 20; attempt++) {
            Thread.sleep(500);
            HttpResponse<String> current = get("/api/orders/" + orderId, user);
            if (current.body().contains("\"status\":\"CANCELLED\"")) {
                status = "CANCELLED";
                break;
            }
        }

        assertThat(status).isEqualTo("CANCELLED");
        assertThat(seatStatusCount(setup.performanceId(), "LOCKED")).isZero();

        HttpResponse<String> reorder = createSeatedOrder(
                user, setup.performanceId(), chosen, "timeout-key-2");
        assertThat(reorder.statusCode()).isEqualTo(201);
    }
}
