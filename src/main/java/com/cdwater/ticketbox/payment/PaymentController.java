package com.cdwater.ticketbox.payment;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.common.HmacSigner;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final String callbackSecret;

    public PaymentController(PaymentService paymentService,
                             @Value("${app.payment.callback-secret}") String callbackSecret) {
        this.paymentService = paymentService;
        this.callbackSecret = callbackSecret;
    }

    @PostMapping("/api/orders/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(Authentication authentication,
                                  @PathVariable Long id,
                                  @Valid @RequestBody PaymentRequest request) {
        return paymentService.create(authentication.getName(), id, request);
    }

    @PostMapping("/api/payments/callback")
    public Map<String, String> callback(
            @RequestHeader(value = "X-Mock-Signature", required = false) String signature,
            @RequestBody CallbackPayload payload) {
        if (payload.providerTxnNo() == null || payload.status() == null || payload.amount() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CALLBACK", "Malformed callback");
        }
        String canonical = MockPaymentGateway.canonical(
                payload.providerTxnNo(), payload.status(), payload.amount());
        if (!HmacSigner.verify(callbackSecret, canonical, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Invalid callback signature");
        }
        paymentService.handleCallback(payload);
        return Map.of("status", "OK");
    }
}
