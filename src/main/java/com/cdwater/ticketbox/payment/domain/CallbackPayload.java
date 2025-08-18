package com.cdwater.ticketbox.payment.domain;

public record CallbackPayload(
        String providerTxnNo,
        String status,
        String amount) {
}
