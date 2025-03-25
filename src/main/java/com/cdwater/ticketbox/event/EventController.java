package com.cdwater.ticketbox.event;

import com.cdwater.ticketbox.common.PageResponse;
import com.cdwater.ticketbox.common.Paging;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final EventMapper eventMapper;

    public EventController(EventService eventService, EventMapper eventMapper) {
        this.eventService = eventService;
        this.eventMapper = eventMapper;
    }

    @GetMapping
    public PageResponse<EventSummary> list(
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return PageResponse.from(
                eventService.listPublished(city, Paging.newestFirst(page, size)),
                eventMapper::toSummary);
    }

    @GetMapping("/{id}")
    public EventDetail detail(@PathVariable Long id) {
        return eventMapper.toDetail(eventService.findPublished(id));
    }
}
