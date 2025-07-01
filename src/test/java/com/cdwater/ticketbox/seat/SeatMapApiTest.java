package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SeatMapApiTest extends ApiIntegrationTest {

    @Test
    void seatedPerformanceReturnsSeatMap() throws Exception {
        String admin = adminToken();
        long eventId = publishedEvent(admin, "座位图测试演出");
        long performanceId = performance(admin, eventId, "座位图场馆");
        long tierId = priceTier(admin, performanceId, "内场", "680.00", 0);

        HttpResponse<String> generated = generateSection(admin, performanceId, tierId, "内场A区", 2, 3);
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
        String admin = adminToken();
        long eventId = publishedEvent(admin, "无座座位图测试演出");
        long performanceId = performance(admin, eventId, "无座场馆");
        priceTier(admin, performanceId, "站席", "280.00", 120);

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
        String admin = adminToken();
        long eventId = publishedEvent(admin, "票档归属测试演出");
        long first = performance(admin, eventId, "第一个场馆");
        long second = performance(admin, eventId, "第二个场馆");
        long foreignTier = priceTier(admin, second, "别处票档", "100.00", 10);

        HttpResponse<String> response = generateSection(admin, first, foreignTier, "A区", 2, 2);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("PRICE_TIER_MISMATCH");
    }

    @Test
    void duplicateSectionNameIsRejected() throws Exception {
        String admin = adminToken();
        long eventId = publishedEvent(admin, "区域重名测试演出");
        long performanceId = performance(admin, eventId, "重名场馆");
        long tierId = priceTier(admin, performanceId, "内场", "100.00", 0);

        assertThat(generateSection(admin, performanceId, tierId, "A区", 2, 2).statusCode()).isEqualTo(201);

        HttpResponse<String> duplicate = generateSection(admin, performanceId, tierId, "A区", 2, 2);

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("SECTION_NAME_TAKEN");
    }

    @Test
    void regularUserCannotGenerateSections() throws Exception {
        String admin = adminToken();
        long eventId = publishedEvent(admin, "权限测试演出");
        long performanceId = performance(admin, eventId, "权限场馆");
        long tierId = priceTier(admin, performanceId, "内场", "100.00", 0);
        String user = userToken("seat_user");

        HttpResponse<String> response = generateSection(user, performanceId, tierId, "A区", 2, 2);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("FORBIDDEN");
    }

    @Test
    void draftEventSeatMapIsHidden() throws Exception {
        String admin = adminToken();
        long eventId = event(admin, "草稿座位图测试演出");
        long performanceId = performance(admin, eventId, "草稿场馆");
        long tierId = priceTier(admin, performanceId, "内场", "100.00", 0);
        generateSection(admin, performanceId, tierId, "A区", 2, 2);

        HttpResponse<String> response = get("/api/performances/" + performanceId + "/seat-map", null);
        assertThat(response.statusCode()).isEqualTo(404);

        HttpResponse<String> adminSections = get(
                "/api/admin/performances/" + performanceId + "/sections", admin);
        assertThat(adminSections.statusCode()).isEqualTo(200);
        assertThat(adminSections.body()).contains("A区");
    }
}
