package com.cdwater.ticketbox.event;

import com.cdwater.ticketbox.common.PageResponse;
import com.cdwater.ticketbox.common.Paging;
import com.cdwater.ticketbox.performance.PerformanceService;
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
    private final PerformanceService performanceService;

    public EventController(EventService eventService, EventMapper eventMapper, PerformanceService performanceService) {
        this.eventService = eventService;
        this.eventMapper = eventMapper;
        this.performanceService = performanceService;
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
        Event event = eventService.findPublished(id);
        return eventMapper.toDetail(event, performanceService.listByEvent(event.getId()));
    }
}
