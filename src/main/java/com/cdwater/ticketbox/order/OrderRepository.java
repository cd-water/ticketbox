package com.cdwater.ticketbox.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByPerformanceId(Long performanceId, Pageable pageable);

    Page<Order> findByPerformanceIdAndStatus(Long performanceId, OrderStatus status, Pageable pageable);

    List<Order> findByStatus(OrderStatus status);

    @Query("select o.id from Order o where o.status = :status and o.expiresAt < :now")
    List<Long> findIdsByStatusAndExpiresAtBefore(@Param("status") OrderStatus status, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = :cancelled where o.id = :id and o.status = :pending")
    int cancelIfPending(@Param("id") Long id,
                        @Param("cancelled") OrderStatus cancelled,
                        @Param("pending") OrderStatus pending);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o set o.status = :paid, o.paidAt = :paidAt
            where o.id = :id and o.status = :pending and o.expiresAt > :now
            """)
    int markPaid(@Param("id") Long id,
                 @Param("paid") OrderStatus paid,
                 @Param("pending") OrderStatus pending,
                 @Param("paidAt") Instant paidAt,
                 @Param("now") Instant now);
}
