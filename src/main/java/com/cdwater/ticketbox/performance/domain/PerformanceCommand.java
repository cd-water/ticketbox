package com.cdwater.ticketbox.performance.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PerformanceCommand(
        @NotNull Long eventId,
        @NotNull Instant startsAt,
        @NotBlank @Size(max = 120) String venueName,
        @NotBlank @Size(max = 200) String venueAddress,
        @NotNull Instant saleStartAt) {

    public PerformanceCommand normalized() {
        return new PerformanceCommand(eventId, startsAt, venueName.trim(), venueAddress.trim(), saleStartAt);
    }
}
