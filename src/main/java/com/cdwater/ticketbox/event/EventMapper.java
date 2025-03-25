package com.cdwater.ticketbox.event;

import com.cdwater.ticketbox.storage.StorageService;
import org.springframework.stereotype.Component;

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

    public EventDetail toDetail(Event event) {
        return new EventDetail(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCity(),
                storage.presignedUrl(event.getCoverObjectKey()));
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
