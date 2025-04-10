package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.performance.PriceTierView;
import com.cdwater.ticketbox.performance.SeatingMode;

import java.util.List;

public record SeatMapResponse(
        Long eventId,
        Long performanceId,
        SeatingMode seatingMode,
        List<SectionView> sections,
        List<PriceTierView> priceTiers) {
}
