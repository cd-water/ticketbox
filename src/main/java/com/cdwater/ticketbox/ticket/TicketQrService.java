package com.cdwater.ticketbox.ticket;

import com.cdwater.ticketbox.common.HmacSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TicketQrService {

    private final String secret;

    public TicketQrService(@Value("${app.ticket.qr-secret}") String secret) {
        this.secret = secret;
    }

    public String payload(Ticket ticket) {
        long issuedAt = ticket.getIssuedAt().getEpochSecond();
        String canonical = TicketQrCodec.canonical(ticket.getId(), ticket.getPerformanceId(), issuedAt);
        return TicketQrCodec.encode(
                ticket.getId(), ticket.getPerformanceId(), issuedAt, HmacSigner.sign(secret, canonical));
    }
}
