package com.cdwater.ticketbox.performance;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceApiTest extends ApiIntegrationTest {

    @Test
    void performancesAndPriceTiersAreVisibleOnPublishedEvent() throws Exception {
        String token = adminToken();
        long eventId = createPublishedEvent(token, "场次展示测试演出");
        long performanceId = createPerformance(token, eventId, "梅赛德斯-奔驰文化中心");

        createTier(token, performanceId, "内场", "680.00", 100);
        createTier(token, performanceId, "看台", "280.00", 200);

        HttpResponse<String> detail = get("/api/events/" + eventId, null);

        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body())
                .contains("梅赛德斯-奔驰文化中心")
                .contains("内场")
                .contains("看台")
                .contains("280.00")
                .contains("680.00")
                .contains("\"performances\":[");
    }

    @Test
    void performanceForUnknownEventIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/admin/performances",
                performanceJson(999999L, "未知场馆", "2026-12-01T12:00:00Z"), adminToken());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("EVENT_NOT_FOUND");
    }

    @Test
    void priceTierForUnknownPerformanceIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/admin/price-tiers",
                "{\"performanceId\":999999,\"name\":\"内场\",\"price\":100.00,\"stock\":10}",
                adminToken());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("PERFORMANCE_NOT_FOUND");
    }

    @Test
    void regularUserCannotManagePerformances() throws Exception {
        post("/api/auth/register", "{\"username\":\"performance_user\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"performance_user\",\"password\":\"secret123\"}");
        String token = tokenFrom(login.body());

        HttpResponse<String> response = get("/api/admin/performances?eventId=1", token);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("FORBIDDEN");
    }

    @Test
    void updatingPerformanceAndTierIsReflected() throws Exception {
        String token = adminToken();
        long eventId = createPublishedEvent(token, "场次更新测试演出");
        long performanceId = createPerformance(token, eventId, "旧场馆");
        long tierId = createTier(token, performanceId, "内场", "100.00", 50);

        HttpResponse<String> updatedPerformance = put("/api/admin/performances/" + performanceId,
                performanceUpdateJson("新场馆"), token);
        assertThat(updatedPerformance.statusCode()).isEqualTo(200);
        assertThat(updatedPerformance.body()).contains("新场馆");

        HttpResponse<String> updatedTier = put("/api/admin/price-tiers/" + tierId,
                "{\"name\":\"内场\",\"price\":120.00,\"stock\":150}", token);
        assertThat(updatedTier.statusCode()).isEqualTo(200);
        assertThat(updatedTier.body()).contains("\"stock\":150");

        assertThat(get("/api/events/" + eventId, null).body())
                .contains("新场馆")
                .contains("120.00")
                .contains("\"stock\":150");
    }

    @Test
    void performancesAndTiersAreSorted() throws Exception {
        String token = adminToken();
        long eventId = createPublishedEvent(token, "排序测试演出");
        long late = createPerformance(token, eventId, "晚场场馆", "2026-12-21T12:00:00Z");
        createPerformance(token, eventId, "早场场馆", "2026-12-20T12:00:00Z");
        createTier(token, late, "内场", "680.00", 10);
        createTier(token, late, "看台", "280.00", 10);

        String body = get("/api/events/" + eventId, null).body();

        assertThat(body.indexOf("早场场馆")).isLessThan(body.indexOf("晚场场馆"));
        assertThat(body.indexOf("看台")).isLessThan(body.indexOf("内场"));
    }

    @Test
    void negativePriceOrStockIsRejected() throws Exception {
        String token = adminToken();
        long eventId = createPublishedEvent(token, "校验测试演出");
        long performanceId = createPerformance(token, eventId, "校验场馆");

        HttpResponse<String> negativePrice = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\",\"price\":-1.00,\"stock\":10}",
                token);
        assertThat(negativePrice.statusCode()).isEqualTo(400);
        assertThat(negativePrice.body()).contains("VALIDATION_ERROR");

        HttpResponse<String> negativeStock = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"内场\",\"price\":100.00,\"stock\":-1}",
                token);
        assertThat(negativeStock.statusCode()).isEqualTo(400);
        assertThat(negativeStock.body()).contains("VALIDATION_ERROR");

        long tierId = createTier(token, performanceId, "内场", "100.00", 10);
        HttpResponse<String> missingStock = put("/api/admin/price-tiers/" + tierId,
                "{\"name\":\"内场\",\"price\":100.00}", token);
        assertThat(missingStock.statusCode()).isEqualTo(400);
        assertThat(missingStock.body()).contains("VALIDATION_ERROR");
    }

    @Test
    void adminCanListPerformancesForEvent() throws Exception {
        String token = adminToken();
        long eventId = createPublishedEvent(token, "管理列表测试演出");
        long performanceId = createPerformance(token, eventId, "列表场馆");
        createTier(token, performanceId, "内场", "100.00", 10);

        HttpResponse<String> response = get("/api/admin/performances?eventId=" + eventId, token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("列表场馆").contains("内场");
    }

    private long createPublishedEvent(String token, String title) throws Exception {
        HttpResponse<String> created = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"上海\"}",
                token);
        assertThat(created.statusCode()).isEqualTo(201);
        long eventId = idFrom(created.body());
        post("/api/admin/events/" + eventId + "/publish", "", token);
        return eventId;
    }

    private long createPerformance(String token, long eventId, String venueName) throws Exception {
        return createPerformance(token, eventId, venueName, "2026-12-01T12:00:00Z");
    }

    private long createPerformance(String token, long eventId, String venueName, String startsAt) throws Exception {
        HttpResponse<String> created = post("/api/admin/performances",
                performanceJson(eventId, venueName, startsAt), token);
        assertThat(created.statusCode()).isEqualTo(201);
        return idFrom(created.body());
    }

    private long createTier(String token, long performanceId, String name, String price, int stock) throws Exception {
        HttpResponse<String> created = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"" + name + "\","
                        + "\"price\":" + price + ",\"stock\":" + stock + "}",
                token);
        assertThat(created.statusCode()).isEqualTo(201);
        return idFrom(created.body());
    }

    private static String performanceJson(long eventId, String venueName, String startsAt) {
        return "{\"eventId\":" + eventId + ","
                + "\"startsAt\":\"" + startsAt + "\","
                + "\"venueName\":\"" + venueName + "\","
                + "\"venueAddress\":\"浦东新区世博大道1200号\","
                + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}";
    }

    private static String performanceUpdateJson(String venueName) {
        return "{\"startsAt\":\"2026-12-01T12:00:00Z\","
                + "\"venueName\":\"" + venueName + "\","
                + "\"venueAddress\":\"浦东新区世博大道1200号\","
                + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}";
    }
}
