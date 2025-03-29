package com.cdwater.ticketbox.performance;

import java.math.BigDecimal;

public record PriceTierView(Long id, String name, BigDecimal price, int stock) {

    public static PriceTierView from(PriceTier tier) {
        return new PriceTierView(tier.getId(), tier.getName(), tier.getPrice(), tier.getStock());
    }
}
