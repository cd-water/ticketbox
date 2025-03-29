package com.cdwater.ticketbox.event;

import com.cdwater.ticketbox.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class EventApiTest extends ApiIntegrationTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    void draftEventIsInvisibleUntilPublished() throws Exception {
        String token = adminToken();
        long id = createEvent(token, "未发布的演出", "上海");

        assertThat(get("/api/events", null).body()).doesNotContain("未发布的演出");
        assertThat(get("/api/events/" + id, null).statusCode()).isEqualTo(404);

        HttpResponse<String> published = post("/api/admin/events/" + id + "/publish", "", token);
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(published.body()).contains("PUBLISHED");

        HttpResponse<String> list = get("/api/events", null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("未发布的演出");

        HttpResponse<String> detail = get("/api/events/" + id, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("上海").contains("演出简介");
    }

    @Test
    void coverUploadIsServedThroughPresignedUrl() throws Exception {
        String token = adminToken();

        HttpResponse<String> upload = postMultipart("/api/admin/files", "cover.png", "image/png", PNG_BYTES, token);
        assertThat(upload.statusCode()).isEqualTo(200);
        String objectKey = jsonField(upload.body(), "key");

        HttpResponse<String> created = post("/api/admin/events",
                "{\"title\":\"带封面的演出\",\"description\":\"演出简介\",\"city\":\"北京\",\"coverObjectKey\":\""
                        + objectKey + "\"}",
                token);
        assertThat(created.statusCode()).isEqualTo(201);
        long id = idFrom(created.body());
        post("/api/admin/events/" + id + "/publish", "", token);

        HttpResponse<String> detail = get("/api/events/" + id, null);
        String coverUrl = jsonField(detail.body(), "coverUrl");

        HttpResponse<byte[]> image = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(coverUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).hasSize(PNG_BYTES.length);
    }

    @Test
    void spoofedImageTypeIsRejected() throws Exception {
        HttpResponse<String> upload = postMultipart("/api/admin/files", "fake.png",
                "image/png", "not an image".getBytes(), adminToken());

        assertThat(upload.statusCode()).isEqualTo(400);
        assertThat(upload.body()).contains("INVALID_FILE_TYPE");
    }

    @Test
    void oversizedUploadIsRejected() throws Exception {
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];

        HttpResponse<String> upload = postMultipart("/api/admin/files", "big.png",
                "image/png", tooLarge, adminToken());

        assertThat(upload.statusCode()).isEqualTo(400);
        assertThat(upload.body()).contains("FILE_TOO_LARGE");
    }

    @Test
    void regularUserCannotManageEvents() throws Exception {
        post("/api/auth/register", "{\"username\":\"event_user\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"event_user\",\"password\":\"secret123\"}");
        String token = tokenFrom(login.body());

        HttpResponse<String> response = get("/api/admin/events", token);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("FORBIDDEN");
    }

    @Test
    void cityFilterReturnsOnlyMatchingEvents() throws Exception {
        String token = adminToken();
        long hangzhou = createEvent(token, "杭州测试演出", "测试城甲");
        long chengdu = createEvent(token, "成都测试演出", "测试城乙");
        post("/api/admin/events/" + hangzhou + "/publish", "", token);
        post("/api/admin/events/" + chengdu + "/publish", "", token);

        HttpResponse<String> filtered = get("/api/events?city=测试城甲", null);

        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(filtered.body()).contains("杭州测试演出").doesNotContain("成都测试演出");
        assertThat(filtered.body()).contains("\"page\":0").contains("\"size\":12").contains("\"total\":1");
    }

    @Test
    void updateChangesPublishedEvent() throws Exception {
        String token = adminToken();
        long id = createEvent(token, "旧标题", "广州");
        post("/api/admin/events/" + id + "/publish", "", token);

        HttpResponse<String> updated = put("/api/admin/events/" + id,
                "{\"title\":\"新标题\",\"description\":\"更新后的简介\",\"city\":\"深圳\"}", token);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("新标题").contains("深圳");

        assertThat(get("/api/events/" + id, null).body())
                .contains("新标题")
                .contains("更新后的简介");
    }

    private long createEvent(String token, String title, String city) throws Exception {
        HttpResponse<String> response = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"" + city + "\"}",
                token);
        assertThat(response.statusCode()).isEqualTo(201);
        return idFrom(response.body());
    }
}
