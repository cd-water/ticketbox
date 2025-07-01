package com.cdwater.ticketbox.auth;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiTest extends ApiIntegrationTest {

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
    void shortPasswordIsRejected() throws Exception {
        HttpResponse<String> register = post("/api/auth/register",
                "{\"username\":\"dave\",\"password\":\"123\"}");

        assertThat(register.statusCode()).isEqualTo(400);
        assertThat(register.body()).contains("VALIDATION_ERROR");
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
}
