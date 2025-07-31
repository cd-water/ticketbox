package com.cdwater.ticketbox.performance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByPerformanceIdOrderBySectionIdAscRowNoAscSeatNoAsc(Long performanceId);
}
