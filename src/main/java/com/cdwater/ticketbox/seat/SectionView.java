package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.performance.domain.PriceTierView;

import java.util.List;

public record SectionView(
        Long id,
        String name,
        PriceTierView priceTier,
        int rowCount,
        int seatsPerRow,
        List<SeatView> seats) {
}
