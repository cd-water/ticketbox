package com.cdwater.ticketbox.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "price_tier_id", nullable = false)
    private Long priceTierId;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "attendee_name", nullable = false, length = 64)
    private String attendeeName;

    @Column(name = "attendee_id_no", nullable = false, length = 64)
    private String attendeeIdNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    protected Ticket() {
    }

    public Ticket(Long orderId, Long performanceId, Long priceTierId, Long seatId,
                  String attendeeName, String attendeeIdNo) {
        this.orderId = orderId;
        this.performanceId = performanceId;
        this.priceTierId = priceTierId;
        this.seatId = seatId;
        this.attendeeName = attendeeName;
        this.attendeeIdNo = attendeeIdNo;
        this.status = TicketStatus.ISSUED;
    }

    @PrePersist
    void onCreate() {
        this.issuedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPerformanceId() {
        return performanceId;
    }

    public Long getPriceTierId() {
        return priceTierId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public String getAttendeeIdNo() {
        return attendeeIdNo;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }
}
