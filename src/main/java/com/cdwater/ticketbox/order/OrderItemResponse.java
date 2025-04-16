package com.cdwater.ticketbox.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long seatId,
        Integer rowNo,
        Integer seatNo,
        String sectionName,
        String tierName,
        BigDecimal unitPrice) {
}
