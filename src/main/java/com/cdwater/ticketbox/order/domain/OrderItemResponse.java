package com.cdwater.ticketbox.order.domain;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long seatId,
        Integer rowNo,
        Integer seatNo,
        String sectionName,
        String tierName,
        int quantity,
        BigDecimal unitPrice) {
}
