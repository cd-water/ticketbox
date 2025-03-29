package com.cdwater.ticketbox.event;

import com.cdwater.ticketbox.performance.PerformanceSummary;

import java.util.List;

public record EventDetail(
        Long id,
        String title,
        String description,
        String city,
        String coverUrl,
        List<PerformanceSummary> performances) {
}
