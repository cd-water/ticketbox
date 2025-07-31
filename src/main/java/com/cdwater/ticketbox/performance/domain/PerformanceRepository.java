package com.cdwater.ticketbox.performance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    List<Performance> findByEventIdOrderByStartsAtAsc(Long eventId);
}
