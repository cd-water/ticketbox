package com.cdwater.ticketbox.performance;

import java.time.Instant;
import java.util.List;

public record PerformanceSummary(
        Long id,
        Instant startsAt,
        String venueName,
        String venueAddress,
        Instant saleStartAt,
        SeatingMode seatingMode,
        boolean waitingRoomEnabled,
        int admissionRate,
        int tokenTtlSeconds,
        List<PriceTierView> priceTiers) {
}
