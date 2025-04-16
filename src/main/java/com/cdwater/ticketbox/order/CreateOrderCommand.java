package com.cdwater.ticketbox.order;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderCommand(
        @NotNull Long performanceId,
        @NotEmpty @Size(max = 4) List<Long> seatIds) {
}
