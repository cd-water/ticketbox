package com.cdwater.ticketbox.order.domain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderCommand(
        @NotNull Long performanceId,
        @Size(max = 4) List<Long> seatIds,
        Long priceTierId,
        @Min(1) @Max(4) Integer quantity) {

    @AssertTrue(message = "Provide either seatIds or priceTierId with quantity")
    public boolean isModeValid() {
        boolean seated = seatIds != null && !seatIds.isEmpty();
        boolean generalAdmission = priceTierId != null && quantity != null;
        return seated ^ generalAdmission;
    }
}
