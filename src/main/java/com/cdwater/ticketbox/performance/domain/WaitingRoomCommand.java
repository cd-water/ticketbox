package com.cdwater.ticketbox.performance.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WaitingRoomCommand(
        @NotNull Boolean enabled,
        @Min(1) @Max(1000) int admissionRate,
        @Min(60) @Max(3600) int tokenTtlSeconds) {
}
