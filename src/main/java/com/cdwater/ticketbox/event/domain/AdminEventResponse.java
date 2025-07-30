package com.cdwater.ticketbox.event.domain;

import java.time.Instant;

public record AdminEventResponse(
        Long id,
        String title,
        String description,
        String city,
        String coverObjectKey,
        String coverUrl,
        EventStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
