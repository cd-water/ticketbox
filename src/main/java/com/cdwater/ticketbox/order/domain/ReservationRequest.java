package com.cdwater.ticketbox.order.domain;

import java.util.List;

public record ReservationRequest(
        Long performanceId,
        List<Long> seatIds,
        Long priceTierId,
        Integer quantity) {
}
