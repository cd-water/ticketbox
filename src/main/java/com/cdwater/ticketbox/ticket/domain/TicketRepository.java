package com.cdwater.ticketbox.ticket.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByOrderIdOrderByIdAsc(Long orderId);

    @Query("select t.seatId from Ticket t where t.performanceId = :performanceId and t.seatId is not null")
    Set<Long> findSeatIdsByPerformanceId(@Param("performanceId") Long performanceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ticket t set t.status = :checkedIn, t.checkedInAt = :now
            where t.id = :id and t.status = :issued
            """)
    int checkIn(@Param("id") Long id,
                @Param("checkedIn") TicketStatus checkedIn,
                @Param("issued") TicketStatus issued,
                @Param("now") Instant now);
}
