package com.cdwater.ticketbox.performance;

import com.cdwater.ticketbox.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceTierService {

    private final PriceTierRepository priceTiers;
    private final PerformanceRepository performances;

    public PriceTierService(PriceTierRepository priceTiers, PerformanceRepository performances) {
        this.priceTiers = priceTiers;
        this.performances = performances;
    }

    @Transactional
    public PriceTier create(PriceTierCommand command) {
        requirePerformance(command.performanceId());
        return priceTiers.save(new PriceTier(
                command.performanceId(),
                command.name(),
                command.price(),
                command.stock()));
    }

    @Transactional
    public PriceTier update(Long id, PriceTierUpdateCommand command) {
        PriceTier tier = priceTiers.findById(id).orElseThrow(PriceTierService::notFound);
        tier.updateDetails(
                tier.getPerformanceId(),
                command.name(),
                command.price(),
                command.stock());
        return tier;
    }

    private void requirePerformance(Long performanceId) {
        if (!performances.existsById(performanceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "Performance not found");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PRICE_TIER_NOT_FOUND", "Price tier not found");
    }
}
