package com.cdwater.ticketbox.ticket.domain;

public record TicketResponse(
        Long id,
        Long seatId,
        Integer rowNo,
        Integer seatNo,
        String sectionName,
        String tierName,
        String attendeeName,
        String attendeeIdNo,
        String status,
        String qrPayload) {
}
