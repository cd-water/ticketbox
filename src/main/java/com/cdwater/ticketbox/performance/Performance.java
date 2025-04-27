package com.cdwater.ticketbox.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "performance")
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "venue_name", nullable = false, length = 120)
    private String venueName;

    @Column(name = "venue_address", nullable = false, length = 200)
    private String venueAddress;

    @Column(name = "sale_start_at", nullable = false)
    private Instant saleStartAt;

    @Column(name = "waiting_room_enabled", nullable = false)
    private boolean waitingRoomEnabled;

    @Column(name = "admission_rate", nullable = false)
    private int admissionRate;

    @Column(name = "token_ttl_seconds", nullable = false)
    private int tokenTtlSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Performance() {
    }

    public Performance(Long eventId, Instant startsAt, String venueName, String venueAddress, Instant saleStartAt) {
        this.eventId = eventId;
        this.startsAt = startsAt;
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.saleStartAt = saleStartAt;
        this.waitingRoomEnabled = false;
        this.admissionRate = 10;
        this.tokenTtlSeconds = 600;
    }

    public void updateDetails(Long eventId, Instant startsAt, String venueName, String venueAddress, Instant saleStartAt) {
        this.eventId = eventId;
        this.startsAt = startsAt;
        this.venueName = venueName;
        this.venueAddress = venueAddress;
        this.saleStartAt = saleStartAt;
    }

    public void updateWaitingRoom(boolean enabled, int admissionRate, int tokenTtlSeconds) {
        this.waitingRoomEnabled = enabled;
        this.admissionRate = admissionRate;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public boolean isWaitingRoomEnabled() {
        return waitingRoomEnabled;
    }

    public int getAdmissionRate() {
        return admissionRate;
    }

    public int getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueAddress() {
        return venueAddress;
    }

    public Instant getSaleStartAt() {
        return saleStartAt;
    }
}
