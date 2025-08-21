package com.cdwater.ticketbox.ticket.domain;

import java.time.Instant;

public record CheckInResponse(
        Long ticketId,
        String attendeeName,
        String attendeeIdNo,
        String tierName,
        String sectionName,
        Integer rowNo,
        Integer seatNo,
        String status,
        Instant checkedInAt,
        String eventTitle,
        Instant startsAt,
        String venueName) {
}
