package com.cdwater.ticketbox.event;

import com.cdwater.ticketbox.common.PageResponse;
import com.cdwater.ticketbox.common.Paging;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final EventService eventService;
    private final EventMapper eventMapper;

    public AdminEventController(EventService eventService, EventMapper eventMapper) {
        this.eventService = eventService;
        this.eventMapper = eventMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEventResponse create(@Valid @RequestBody EventCommand command) {
        return eventMapper.toAdmin(eventService.create(command.normalized()));
    }

    @GetMapping
    public PageResponse<AdminEventResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(eventService.listAdmin(Paging.newestFirst(page, size)), eventMapper::toAdmin);
    }

    @GetMapping("/{id}")
    public AdminEventResponse get(@PathVariable Long id) {
        return eventMapper.toAdmin(eventService.findAdmin(id));
    }

    @PutMapping("/{id}")
    public AdminEventResponse update(@PathVariable Long id, @Valid @RequestBody EventCommand command) {
        return eventMapper.toAdmin(eventService.update(id, command.normalized()));
    }

    @PostMapping("/{id}/publish")
    public AdminEventResponse publish(@PathVariable Long id) {
        return eventMapper.toAdmin(eventService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public AdminEventResponse unpublish(@PathVariable Long id) {
        return eventMapper.toAdmin(eventService.unpublish(id));
    }
}
