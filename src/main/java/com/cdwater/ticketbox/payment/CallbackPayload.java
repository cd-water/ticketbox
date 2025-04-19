package com.cdwater.ticketbox.payment;

public record CallbackPayload(
        String providerTxnNo,
        String status,
        String amount) {
}
