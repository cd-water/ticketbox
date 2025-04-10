package com.cdwater.ticketbox.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findByPerformanceIdOrderByIdAsc(Long performanceId);

    boolean existsByPerformanceId(Long performanceId);

    boolean existsByPerformanceIdAndName(Long performanceId, String name);
}
