package com.cdwater.ticketbox.performance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PriceTierUpdateCommand(
        @NotBlank @Size(max = 64) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer stock) {

    public PriceTierUpdateCommand normalized() {
        return new PriceTierUpdateCommand(name.trim(), price, stock);
    }
}
