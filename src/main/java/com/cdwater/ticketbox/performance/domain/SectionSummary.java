package com.cdwater.ticketbox.performance.domain;

public record SectionSummary(
        Long id,
        String name,
        PriceTierView priceTier,
        int rowCount,
        int seatsPerRow,
        int seatCount) {
}
