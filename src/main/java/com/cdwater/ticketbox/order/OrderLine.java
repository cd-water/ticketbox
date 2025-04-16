package com.cdwater.ticketbox.order;

import java.math.BigDecimal;

public record OrderLine(Long seatId, Long priceTierId, BigDecimal unitPrice) {
}
