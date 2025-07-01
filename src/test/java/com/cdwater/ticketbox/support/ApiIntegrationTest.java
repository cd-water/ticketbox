package com.cdwater.ticketbox.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("ticketbox")
            .withUsername("ticketmysql")
            .withPassword("ticketmysql");

    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.11"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "ticketredis");

    protected static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "ticketminio")
            .withEnv("MINIO_ROOT_PASSWORD", "ticketminio")
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    static {
        MYSQL.start();
        REDIS.start();
        MINIO.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MINIO.stop();
            REDIS.stop();
            MYSQL.stop();
        }));
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.minio.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("app.minio.access-key", () -> "ticketminio");
        registry.add("app.minio.secret-key", () -> "ticketminio");
        registry.add("app.minio.bucket", () -> "ticketbox");
    }

    @Value("${local.server.port}")
    protected int port;

    private final HttpClient client = HttpClient.newHttpClient();

    protected HttpResponse<String> post(String path, String body) throws Exception {
        return post(path, body, null);
    }

    protected HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return send(request, token);
    }

    protected HttpResponse<String> post(String path, String body, String token,
                                        Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        return send(request, token);
    }

    protected HttpResponse<String> put(String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        return send(request, token);
    }

    protected HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        return send(request, token);
    }

    protected HttpResponse<String> postMultipart(String path, String filename, String contentType,
                                                 byte[] content, String token) throws Exception {
        String boundary = "----ticketbox" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        return send(request, token);
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected String adminToken() throws Exception {
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"admin_test\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        return tokenFrom(login.body());
    }

    protected static String tokenFrom(String body) {
        Matcher matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    protected static long idFrom(String body) {
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    protected static String jsonField(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1).replace("\\/", "/");
    }

    protected record SeatedPerformance(long eventId, long performanceId, long tierId, List<Long> seatIds) {
    }

    protected record GaPerformance(long eventId, long performanceId, long tierId) {
    }

    protected String userToken(String username) throws Exception {
        post("/api/auth/register", "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        return tokenFrom(login.body());
    }

    protected long event(String admin, String title) throws Exception {
        HttpResponse<String> created = post("/api/admin/events",
                "{\"title\":\"" + title + "\",\"description\":\"演出简介\",\"city\":\"上海\"}", admin);
        assertThat(created.statusCode()).isEqualTo(201);
        return idFrom(created.body());
    }

    protected void publish(String admin, long eventId) throws Exception {
        post("/api/admin/events/" + eventId + "/publish", "", admin);
    }

    protected long publishedEvent(String admin, String title) throws Exception {
        long eventId = event(admin, title);
        publish(admin, eventId);
        return eventId;
    }

    protected long performance(String admin, long eventId, String venueName) throws Exception {
        return performance(admin, eventId, venueName, "2026-12-01T12:00:00Z", "2026-11-20T02:00:00Z");
    }

    protected long performance(String admin, long eventId, String venueName,
                               String startsAt, String saleStartAt) throws Exception {
        HttpResponse<String> created = post("/api/admin/performances",
                "{\"eventId\":" + eventId + ","
                        + "\"startsAt\":\"" + startsAt + "\","
                        + "\"venueName\":\"" + venueName + "\","
                        + "\"venueAddress\":\"浦东新区世博大道1200号\","
                        + "\"saleStartAt\":\"" + saleStartAt + "\"}",
                admin);
        assertThat(created.statusCode()).isEqualTo(201);
        return idFrom(created.body());
    }

    protected long priceTier(String admin, long performanceId, String name,
                             String price, int stock) throws Exception {
        HttpResponse<String> created = post("/api/admin/price-tiers",
                "{\"performanceId\":" + performanceId + ",\"name\":\"" + name + "\","
                        + "\"price\":" + price + ",\"stock\":" + stock + "}",
                admin);
        assertThat(created.statusCode()).isEqualTo(201);
        return idFrom(created.body());
    }

    protected HttpResponse<String> generateSection(String admin, long performanceId, long tierId,
                                                   String name, int rows, int seatsPerRow) throws Exception {
        return post("/api/admin/performances/" + performanceId + "/sections",
                "{\"name\":\"" + name + "\",\"priceTierId\":" + tierId + ","
                        + "\"rowCount\":" + rows + ",\"seatsPerRow\":" + seatsPerRow + "}",
                admin);
    }

    protected SeatedPerformance seatedPerformance(String admin, String title) throws Exception {
        long eventId = publishedEvent(admin, title);
        long performanceId = performance(admin, eventId, "测试场馆");
        long tierId = priceTier(admin, performanceId, "内场", "100.00", 0);
        assertThat(generateSection(admin, performanceId, tierId, "A区", 2, 3).statusCode()).isEqualTo(201);
        return new SeatedPerformance(eventId, performanceId, tierId, seatIdsOf(performanceId));
    }

    protected GaPerformance gaPerformance(String admin, String title, int stock) throws Exception {
        return gaPerformance(admin, title, stock, "2026-11-20T02:00:00Z");
    }

    protected GaPerformance gaPerformance(String admin, String title, int stock, String saleStartAt) throws Exception {
        long eventId = publishedEvent(admin, title);
        long performanceId = performance(admin, eventId, "测试场馆", "2026-12-01T12:00:00Z", saleStartAt);
        long tierId = priceTier(admin, performanceId, "内场", "100.00", stock);
        return new GaPerformance(eventId, performanceId, tierId);
    }

    protected List<Long> seatIdsOf(long performanceId) throws Exception {
        String body = get("/api/performances/" + performanceId + "/seat-map", null).body();
        int start = body.indexOf("\"seats\":[");
        int end = body.indexOf(']', start);
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(body.substring(start, end));
        List<Long> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }

    protected int stockOf(long performanceId) throws Exception {
        String body = get("/api/performances/" + performanceId + "/seat-map", null).body();
        Matcher matcher = Pattern.compile("\"stock\":(\\d+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    protected HttpResponse<String> createSeatedOrder(String userToken, long performanceId,
                                                     List<Long> seatIds, String idempotencyKey) throws Exception {
        String seats = seatIds.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
        return post("/api/orders",
                "{\"performanceId\":" + performanceId + ",\"seatIds\":[" + seats + "]}",
                userToken,
                Map.of("Idempotency-Key", idempotencyKey));
    }

    protected HttpResponse<String> createGaOrder(String userToken, long performanceId, long tierId,
                                                 int quantity, String idempotencyKey) throws Exception {
        return post("/api/orders",
                "{\"performanceId\":" + performanceId + ",\"priceTierId\":" + tierId
                        + ",\"quantity\":" + quantity + "}",
                userToken,
                Map.of("Idempotency-Key", idempotencyKey));
    }

    protected HttpResponse<String> waitForOrderStatus(String userToken, long orderId,
                                                      String status, int attempts) throws Exception {
        HttpResponse<String> order = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Thread.sleep(100);
            order = get("/api/orders/" + orderId, userToken);
            if (order.body().contains("\"status\":\"" + status + "\"")) {
                return order;
            }
        }
        return order;
    }

    protected HttpResponse<String> waitForOrderStatus(String userToken, long orderId, String status) throws Exception {
        return waitForOrderStatus(userToken, orderId, status, 50);
    }

    protected int seatStatusCount(long performanceId, String status) throws Exception {
        return occurrences(get("/api/performances/" + performanceId + "/seat-map", null).body(),
                "\"status\":\"" + status + "\"");
    }

    protected HttpResponse<String> payOrder(String userToken, long orderId, String attendeesJson) throws Exception {
        return post("/api/orders/" + orderId + "/payments",
                "{\"attendees\":" + attendeesJson + "}", userToken);
    }

    protected static String attendees(List<Long> seatIds, String... names) {
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < seatIds.size(); index++) {
            String name = names.length == 1 ? names[0] : names[index];
            entries.add("{\"seatId\":" + seatIds.get(index) + ",\"name\":\"" + name
                    + "\",\"idNo\":\"31010119900101123" + index + "\"}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    protected static String gaAttendees(String... names) {
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            entries.add("{\"name\":\"" + names[index]
                    + "\",\"idNo\":\"31010119900101123" + index + "\"}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    protected static int occurrences(String body, String token) {
        int count = 0;
        int index = body.indexOf(token);
        while (index >= 0) {
            count++;
            index = body.indexOf(token, index + token.length());
        }
        return count;
    }
}
