package com.cdwater.ticketbox.ticket;

import java.util.Optional;

public final class TicketQrCodec {

    private TicketQrCodec() {
    }

    public record Parsed(long ticketId, long performanceId, long issuedAt, String signature) {
    }

    public static String canonical(long ticketId, long performanceId, long issuedAt) {
        return ticketId + "|" + performanceId + "|" + issuedAt;
    }

    public static String encode(long ticketId, long performanceId, long issuedAt, String signature) {
        return ticketId + "." + performanceId + "." + issuedAt + "." + signature;
    }

    public static Optional<Parsed> parse(String payload) {
        if (payload == null) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Parsed(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    parts[3]));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
