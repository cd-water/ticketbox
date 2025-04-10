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
        List<PriceTierView> priceTiers) {
}
