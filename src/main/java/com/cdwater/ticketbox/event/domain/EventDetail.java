package com.cdwater.ticketbox.event.domain;

import com.cdwater.ticketbox.performance.domain.PerformanceSummary;

import java.util.List;

public record EventDetail(
        Long id,
        String title,
        String description,
        String city,
        String coverUrl,
        List<PerformanceSummary> performances) {
}
