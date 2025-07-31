package com.cdwater.ticketbox.performance.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PerformanceUpdateCommand(
        @NotNull Instant startsAt,
        @NotBlank @Size(max = 120) String venueName,
        @NotBlank @Size(max = 200) String venueAddress,
        @NotNull Instant saleStartAt) {

    public PerformanceUpdateCommand normalized() {
        return new PerformanceUpdateCommand(startsAt, venueName.trim(), venueAddress.trim(), saleStartAt);
    }
}
