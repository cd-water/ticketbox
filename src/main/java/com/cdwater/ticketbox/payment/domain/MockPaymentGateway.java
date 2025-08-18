package com.cdwater.ticketbox.payment.domain;

import com.cdwater.ticketbox.common.HmacSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final PaymentRepository payments;
    private final Environment environment;
    private final String secret;
    private final Duration delay;

    public MockPaymentGateway(PaymentRepository payments,
                              Environment environment,
                              @Value("${app.payment.callback-secret}") String secret,
                              @Value("${app.payment.callback-delay:300ms}") Duration delay) {
        this.payments = payments;
        this.environment = environment;
        this.secret = secret;
        this.delay = delay;
    }

    @Async
    public void send(Long paymentId) {
        Payment payment = payments.findById(paymentId).orElse(null);
        if (payment == null) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }

        String canonical = canonical(payment.getProviderTxnNo(), "SUCCESS", payment.getAmount().toPlainString());
        String body = "{\"providerTxnNo\":\"" + payment.getProviderTxnNo() + "\","
                + "\"status\":\"SUCCESS\","
                + "\"amount\":\"" + payment.getAmount().toPlainString() + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(callbackUrl()))
                .header("Content-Type", "application/json")
                .header("X-Mock-Signature", HmacSigner.sign(secret, canonical))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn("Mock payment callback returned {}", response.statusCode());
            }
        } catch (Exception exception) {
            log.warn("Mock payment callback failed for {}", payment.getProviderTxnNo(), exception);
        }
    }

    public static String canonical(String providerTxnNo, String status, String amount) {
        return providerTxnNo + "|" + status + "|" + amount;
    }

    private String callbackUrl() {
        String port = environment.getProperty("local.server.port");
        if (port == null) {
            port = environment.getProperty("server.port", "8888");
        }
        return "http://localhost:" + port + "/api/payments/callback";
    }
}
