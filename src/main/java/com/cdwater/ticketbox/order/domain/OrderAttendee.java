package com.cdwater.ticketbox.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_attendee")
public class OrderAttendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "price_tier_id", nullable = false)
    private Long priceTierId;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "attendee_name", nullable = false, length = 64)
    private String attendeeName;

    @Column(name = "attendee_id_no", nullable = false, length = 64)
    private String attendeeIdNo;

    protected OrderAttendee() {
    }

    public OrderAttendee(Long orderId, Long priceTierId, Long seatId, String attendeeName, String attendeeIdNo) {
        this.orderId = orderId;
        this.priceTierId = priceTierId;
        this.seatId = seatId;
        this.attendeeName = attendeeName;
        this.attendeeIdNo = attendeeIdNo;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
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
}
