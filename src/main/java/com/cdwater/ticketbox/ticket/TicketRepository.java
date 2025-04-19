package com.cdwater.ticketbox.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByOrderIdOrderByIdAsc(Long orderId);

    @Query("select t.seatId from Ticket t where t.performanceId = :performanceId and t.seatId is not null")
    Set<Long> findSeatIdsByPerformanceId(@Param("performanceId") Long performanceId);
}
