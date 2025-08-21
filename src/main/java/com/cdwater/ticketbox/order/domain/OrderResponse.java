package com.cdwater.ticketbox.order.domain;

import com.cdwater.ticketbox.ticket.domain.TicketResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        String status,
        BigDecimal totalAmount,
        Instant expiresAt,
        Instant createdAt,
        Long eventId,
        String eventTitle,
        Instant startsAt,
        String venueName,
        List<OrderItemResponse> items,
        List<TicketResponse> tickets) {
}
