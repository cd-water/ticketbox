package com.cdwater.ticketbox.performance;

import com.cdwater.ticketbox.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class PriceTierService {

    private final PriceTierRepository priceTiers;
    private final PerformanceRepository performances;
    private final GaStockService gaStock;

    public PriceTierService(PriceTierRepository priceTiers,
                            PerformanceRepository performances,
                            GaStockService gaStock) {
        this.priceTiers = priceTiers;
        this.performances = performances;
        this.gaStock = gaStock;
    }

    @Transactional
    public PriceTier create(PriceTierCommand command) {
        requirePerformance(command.performanceId());
        PriceTier tier = priceTiers.save(new PriceTier(
                command.performanceId(),
                command.name(),
                command.price(),
                command.stock()));
        resetStockAfterCommit(tier);
        return tier;
    }

    @Transactional
    public PriceTier update(Long id, PriceTierUpdateCommand command) {
        PriceTier tier = priceTiers.findById(id).orElseThrow(PriceTierService::notFound);
        tier.updateDetails(
                tier.getPerformanceId(),
                command.name(),
                command.price(),
                command.stock());
        resetStockAfterCommit(tier);
        return tier;
    }

    @Transactional(readOnly = true)
    public List<PriceTierView> listViews(Long performanceId) {
        return priceTiers.findByPerformanceIdOrderByPriceAsc(performanceId).stream()
                .map(PriceTierView::from)
                .toList();
    }

    private void resetStockAfterCommit(PriceTier tier) {
        Long performanceId = tier.getPerformanceId();
        Long priceTierId = tier.getId();
        int stock = tier.getStock();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                gaStock.reset(performanceId, priceTierId, stock);
            }
        });
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
