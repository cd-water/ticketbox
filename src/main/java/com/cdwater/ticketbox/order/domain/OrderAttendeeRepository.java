package com.cdwater.ticketbox.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderAttendeeRepository extends JpaRepository<OrderAttendee, Long> {

    List<OrderAttendee> findByOrderIdOrderByIdAsc(Long orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OrderAttendee a where a.orderId = :orderId")
    int deleteForOrder(@Param("orderId") Long orderId);
}
