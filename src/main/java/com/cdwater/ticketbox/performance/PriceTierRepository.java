package com.cdwater.ticketbox.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceTierRepository extends JpaRepository<PriceTier, Long> {

    List<PriceTier> findByPerformanceIdOrderByPriceAsc(Long performanceId);
}
