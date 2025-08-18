package com.cdwater.ticketbox.payment.domain;

import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        String providerTxnNo,
        String status,
        BigDecimal amount) {
}
