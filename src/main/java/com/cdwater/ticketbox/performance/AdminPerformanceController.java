package com.cdwater.ticketbox.performance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/performances")
public class AdminPerformanceController {

    public record WaitingRoomCommand(
            @NotNull Boolean enabled,
            @Min(1) @Max(1000) int admissionRate,
            @Min(60) @Max(3600) int tokenTtlSeconds) {
    }

    private final PerformanceService performanceService;

    public AdminPerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PerformanceSummary create(@Valid @RequestBody PerformanceCommand command) {
        return performanceService.toSummary(performanceService.create(command.normalized()));
    }

    @PutMapping("/{id}")
    public PerformanceSummary update(@PathVariable Long id, @Valid @RequestBody PerformanceUpdateCommand command) {
        return performanceService.toSummary(performanceService.update(id, command.normalized()));
    }

    @PutMapping("/{id}/waiting-room")
    public PerformanceSummary updateWaitingRoom(@PathVariable Long id,
                                                @Valid @RequestBody WaitingRoomCommand command) {
        return performanceService.toSummary(performanceService.updateWaitingRoom(
                id, command.enabled(), command.admissionRate(), command.tokenTtlSeconds()));
    }

    @GetMapping
    public List<PerformanceSummary> list(@RequestParam Long eventId) {
        return performanceService.listByEvent(eventId);
    }
}
