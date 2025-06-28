package com.cdwater.ticketbox.order;

import java.util.List;

public record ReservationRequest(
        Long performanceId,
        List<Long> seatIds,
        Long priceTierId,
        Integer quantity) {
}
