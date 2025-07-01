package com.cdwater.ticketbox.payment;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.order.expiry=3s",
        "app.payment.callback-delay=5s",
        "app.order.sweep-interval=60000"
})
class PaymentExpiryApiTest extends ApiIntegrationTest {

    @Test
    void lateCallbackDoesNotPayExpiredOrder() throws Exception {
        String admin = adminToken();
        SeatedPerformance setup = seatedPerformance(admin, "过期支付测试演出");
        String user = userToken("expiry_pay_user");
        List<Long> chosen = List.of(setup.seatIds().get(0));

        HttpResponse<String> order = createSeatedOrder(user, setup.performanceId(), chosen, "expiry-pay-key");
        assertThat(order.statusCode()).isEqualTo(201);
        long orderId = idFrom(order.body());

        HttpResponse<String> payment = payOrder(user, orderId, attendees(chosen, "张三"));
        assertThat(payment.statusCode()).isEqualTo(201);

        Thread.sleep(6500);

        HttpResponse<String> afterCallback = get("/api/orders/" + orderId, user);
        assertThat(afterCallback.body()).contains("\"status\":\"CANCELLED\"");
        assertThat(occurrences(afterCallback.body(), "\"qrPayload\"")).isZero();
    }
}
