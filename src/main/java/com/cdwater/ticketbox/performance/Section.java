package com.cdwater.ticketbox.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "section")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "price_tier_id", nullable = false)
    private Long priceTierId;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "seats_per_row", nullable = false)
    private int seatsPerRow;

    protected Section() {
    }

    public Section(Long performanceId, String name, Long priceTierId, int rowCount, int seatsPerRow) {
        this.performanceId = performanceId;
        this.name = name;
        this.priceTierId = priceTierId;
        this.rowCount = rowCount;
        this.seatsPerRow = seatsPerRow;
    }

    public Long getId() {
        return id;
    }

    public Long getPerformanceId() {
        return performanceId;
    }

    public String getName() {
        return name;
    }

    public Long getPriceTierId() {
        return priceTierId;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getSeatsPerRow() {
        return seatsPerRow;
    }
}
