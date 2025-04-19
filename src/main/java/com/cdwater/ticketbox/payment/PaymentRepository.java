package com.cdwater.ticketbox.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderTxnNo(String providerTxnNo);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(Long orderId, PaymentStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = :to where p.id = :id and p.status = :from")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("to") PaymentStatus to,
                              @Param("from") PaymentStatus from);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = :expired where p.orderId = :orderId and p.status = :pending")
    int expirePendingForOrder(@Param("orderId") Long orderId,
                              @Param("expired") PaymentStatus expired,
                              @Param("pending") PaymentStatus pending);
}
