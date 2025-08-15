package com.cdwater.ticketbox.order.domain;

import java.math.BigDecimal;

public record OrderLine(Long seatId, Long priceTierId, BigDecimal unitPrice) {
}
