package com.cdwater.ticketbox.event.web;

import com.cdwater.ticketbox.event.domain.AdminEventResponse;
import com.cdwater.ticketbox.event.domain.Event;
import com.cdwater.ticketbox.event.domain.EventDetail;
import com.cdwater.ticketbox.event.domain.EventSummary;
import com.cdwater.ticketbox.performance.domain.PerformanceSummary;
import com.cdwater.ticketbox.storage.StorageService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventMapper {

    private final StorageService storage;

    public EventMapper(StorageService storage) {
        this.storage = storage;
    }

    public EventSummary toSummary(Event event) {
        return new EventSummary(
                event.getId(),
                event.getTitle(),
                event.getCity(),
                storage.presignedUrl(event.getCoverObjectKey()));
    }

    public EventDetail toDetail(Event event, List<PerformanceSummary> performances) {
        return new EventDetail(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCity(),
                storage.presignedUrl(event.getCoverObjectKey()),
                performances);
    }

    public AdminEventResponse toAdmin(Event event) {
        return new AdminEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCity(),
                event.getCoverObjectKey(),
                storage.presignedUrl(event.getCoverObjectKey()),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
