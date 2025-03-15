package com.cdwater.ticketbox.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthApiTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("ticketbox")
            .withUsername("ticketmysql")
            .withPassword("ticketmysql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.11"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "ticketredis");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void registerLoginAndFetchProfile() throws Exception {
        HttpResponse<String> register = post("/api/auth/register",
                "{\"username\":\"alice\",\"password\":\"secret123\"}");
        assertThat(register.statusCode()).isEqualTo(201);
        assertThat(register.body()).contains("\"username\":\"alice\"").contains("\"role\":\"USER\"");

        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"secret123\"}");
        assertThat(login.statusCode()).isEqualTo(200);
        String token = tokenFrom(login.body());

        HttpResponse<String> me = get("/api/me", token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"alice\"").contains("\"role\":\"USER\"");
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        assertThat(post("/api/auth/register", "{\"username\":\"bob\",\"password\":\"secret123\"}").statusCode())
                .isEqualTo(201);

        HttpResponse<String> duplicate = post("/api/auth/register",
                "{\"username\":\"bob\",\"password\":\"secret123\"}");

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("USERNAME_TAKEN");
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        post("/api/auth/register", "{\"username\":\"carol\",\"password\":\"secret123\"}");

        HttpResponse<String> login = post("/api/auth/login",
                "{\"username\":\"carol\",\"password\":\"wrong-password\"}");

        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.body()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void profileRequiresToken() throws Exception {
        HttpResponse<String> me = get("/api/me", null);

        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(me.body()).contains("UNAUTHORIZED");
    }

    @Test
    void concurrentDuplicateRegistrationYieldsOneUser() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        Callable<HttpResponse<String>> attempt = () -> {
            barrier.await();
            return post("/api/auth/register", "{\"username\":\"erin\",\"password\":\"secret123\"}");
        };
        try {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);

            List<Integer> statuses = List.of(first.get().statusCode(), second.get().statusCode())
                    .stream()
                    .sorted()
                    .toList();

            assertThat(statuses).containsExactly(201, 409);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        HttpResponse<String> register = post("/api/auth/register",
                "{\"username\":\"dave\",\"password\":\"123\"}");

        assertThat(register.statusCode()).isEqualTo(400);
        assertThat(register.body()).contains("VALIDATION_ERROR");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String tokenFrom(String body) {
        Matcher matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
