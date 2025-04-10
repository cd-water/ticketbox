package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SeatMapApiTest extends ApiIntegrationTest {

    @Test
    void seatedPerformanceReturnsSeatMap() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, "座位图测试演出", true);
        long performanceId = createPerformance(token, eventId, "座位图场馆");
        long tierId = createTier(token, performanceId, "内场", "680.00", 0);

        HttpResponse<String> generated = generateSection(token, performanceId, "内场A区", tierId, 2, 3);
        assertThat(generated.statusCode()).isEqualTo(201);
        assertThat(generated.body()).contains("\"seatCount\":6");

        HttpResponse<String> seatMap = get("/api/performances/" + performanceId + "/seat-map", null);
        assertThat(seatMap.statusCode()).isEqualTo(200);
        assertThat(seatMap.body())
                .contains("\"seatingMode\":\"SEATED\"")
                .contains("内场A区")
                .contains("680.00");
        assertThat(occurrences(seatMap.body(), "\"status\":\"AVAILABLE\"")).isEqualTo(6);

        assertThat(get("/api/events/" + eventId, null).body()).contains("\"seatingMode\":\"SEATED\"");
    }

    @Test
    void generalAdmissionPerformanceReturnsTiers() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, "无座座位图测试演出", true);
        long performanceId = createPerformance(token, eventId, "无座场馆");
        createTier(token, performanceId, "站席", "280.00", 120);

        HttpResponse<String> seatMap = get("/api/performances/" + performanceId + "/seat-map", null);

        assertThat(seatMap.statusCode()).isEqualTo(200);
        assertThat(seatMap.body())
                .contains("\"seatingMode\":\"GENERAL_ADMISSION\"")
                .contains("站席")
                .contains("\"stock\":120")
                .contains("\"sections\":[]");
    }

    @Test
    void seatMapForUnknownPerformanceIsRejected() throws Exception {
        HttpResponse<String> response = get("/api/performances/999999/seat-map", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("PERFORMANCE_NOT_FOUND");
    }

    @Test
    void sectionWithTierFromAnotherPerformanceIsRejected() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, "票档归属测试演出", true);
        long first = createPerformance(token, eventId, "第一个场馆");
        long second = createPerformance(token, eventId, "第二个场馆");
        long foreignTier = createTier(token, second, "别处票档", "100.00", 10);

        HttpResponse<String> response = generateSection(token, first, "A区", foreignTier, 2, 2);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("PRICE_TIER_MISMATCH");
    }

    @Test
    void duplicateSectionNameIsRejected() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, "区域重名测试演出", true);
        long performanceId = createPerformance(token, eventId, "重名场馆");
        long tierId = createTier(token, performanceId, "内场", "100.00", 0);

        assertThat(generateSection(token, performanceId, "A区", tierId, 2, 2).statusCode()).isEqualTo(201);

        HttpResponse<String> duplicate = generateSection(token, performanceId, "A区", tierId, 2, 2);

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("SECTION_NAME_TAKEN");
    }

    @Test
    void regularUserCannotGenerateSections() throws Exception {
        String admin = adminToken();
        long eventId = createEvent(admin, "权限测试演出", true);
        long performanceId = createPerformance(admin, eventId, "权限场馆");
        long tierId = createTier(admin, performanceId, "内场", "100.00", 0);

        post("/api/auth/register", "{\"username\":\"seat_user\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"seat_user\",\"password\":\"secret123\"}");
        String userToken = tokenFrom(login.body());

        HttpResponse<String> response = generateSection(userToken, performanceId, "A区", tierId, 2, 2);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("FORBIDDEN");
    }

    @Test
    void draftEventSeatMapIsHidden() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, "草稿座位图测试演出", false);
        long performanceId = createPerformance(token, eventId, "草稿场馆");
        long tierId = createTier(token, performanceId, "内场", "100.00", 0);
        generateSection(token, performanceId, "A区", tierId, 2, 2);

        HttpResponse<String> response = get("/api/performances/" + performanceId + "/seat-map", null);
        assertThat(response.statusCode()).isEqualTo(404);

        HttpResponse<String> adminSections = get(
                "/api/admin/performances/" + performanceId + "/sections", token);
        assertThat(adminSections.statusCode()).isEqualTo(200);
        assertThat(adminSections.body()).contains("A区");
    }

    private long createEvent(String token, String title, boolean published) throws Exception {
        HttpResponse<String> created = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"上海\"}",
                token);
        assertThat(created.statusCode()).isEqualTo(201);
        long eventId = idFrom(created.body());
        if (published) {
            post("/api/admin/events/" + eventId + "/publish", "", token);
        }
        return eventId;
    }

    private long createPerformance(String token, long eventId, String venueName) throws Exception {
        HttpResponse<String> created = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"2026-12-01T12:00:00Z\","
                        + "\"venueName\":\"" + venueName + "\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"2026-11-20T02:00:00Z\"}",
                token);
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

    private HttpResponse<String> generateSection(String token, long performanceId, String name,
                                                 long tierId, int rows, int seatsPerRow) throws Exception {
        return post("/api/admin/performances/" + performanceId + "/sections",
                "{\"name\":\"" + name + "\",\"priceTierId\":" + tierId + ","
                        + "\"rowCount\":" + rows + ",\"seatsPerRow\":" + seatsPerRow + "}",
                token);
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
}
