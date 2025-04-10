package com.cdwater.ticketbox.performance;

public record SectionSummary(
        Long id,
        String name,
        PriceTierView priceTier,
        int rowCount,
        int seatsPerRow,
        int seatCount) {
}
