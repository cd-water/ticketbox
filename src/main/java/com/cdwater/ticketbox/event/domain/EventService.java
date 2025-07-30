package com.cdwater.ticketbox.event.domain;

import com.cdwater.ticketbox.common.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository events;

    public EventService(EventRepository events) {
        this.events = events;
    }

    @Transactional
    public Event create(EventCommand command) {
        return events.save(new Event(
                command.title(), command.description(), command.city(), command.coverObjectKey()));
    }

    @Transactional
    public Event update(Long id, EventCommand command) {
        Event event = require(id);
        event.updateDetails(command.title(), command.description(), command.city(), command.coverObjectKey());
        return event;
    }

    @Transactional
    public Event publish(Long id) {
        Event event = require(id);
        event.publish();
        return event;
    }

    @Transactional
    public Event unpublish(Long id) {
        Event event = require(id);
        event.unpublish();
        return event;
    }

    @Transactional(readOnly = true)
    public Event findAdmin(Long id) {
        return require(id);
    }

    @Transactional(readOnly = true)
    public Page<Event> listAdmin(Pageable pageable) {
        return events.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Event> listPublished(String city, Pageable pageable) {
        if (city == null || city.isBlank()) {
            return events.findByStatus(EventStatus.PUBLISHED, pageable);
        }
        return events.findByStatusAndCity(EventStatus.PUBLISHED, city.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Event findPublished(Long id) {
        return events.findById(id)
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(EventService::notFound);
    }

    private Event require(Long id) {
        return events.findById(id).orElseThrow(EventService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found");
    }
}
