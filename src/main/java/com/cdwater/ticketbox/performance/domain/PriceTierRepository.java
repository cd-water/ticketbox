package com.cdwater.ticketbox.performance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceTierRepository extends JpaRepository<PriceTier, Long> {

    List<PriceTier> findByPerformanceIdOrderByPriceAsc(Long performanceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PriceTier t set t.stock = t.stock - :quantity where t.id = :id and t.stock >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PriceTier t set t.stock = t.stock + :quantity where t.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}
