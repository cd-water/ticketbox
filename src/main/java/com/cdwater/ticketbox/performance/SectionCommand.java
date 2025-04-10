package com.cdwater.ticketbox.performance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SectionCommand(
        @NotBlank @Size(max = 64) String name,
        @NotNull Long priceTierId,
        @Min(1) @Max(30) int rowCount,
        @Min(1) @Max(50) int seatsPerRow) {

    public SectionCommand normalized() {
        return new SectionCommand(name.trim(), priceTierId, rowCount, seatsPerRow);
    }
}
