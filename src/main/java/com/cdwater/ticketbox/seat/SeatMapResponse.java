package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.performance.domain.PriceTierView;
import com.cdwater.ticketbox.performance.domain.SeatingMode;

import java.util.List;

public record SeatMapResponse(
        Long eventId,
        Long performanceId,
        SeatingMode seatingMode,
        List<SectionView> sections,
        List<PriceTierView> priceTiers) {
}
