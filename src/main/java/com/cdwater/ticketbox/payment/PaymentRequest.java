package com.cdwater.ticketbox.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PaymentRequest(@NotEmpty List<@Valid Attendee> attendees) {

    public record Attendee(
            @NotNull Long seatId,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 64) String idNo) {
    }
}
