package com.cdwater.ticketbox.performance;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.event.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PerformanceService {

    private final PerformanceRepository performances;
    private final PriceTierService priceTierService;
    private final SectionRepository sections;
    private final EventRepository events;

    public PerformanceService(PerformanceRepository performances,
                              PriceTierService priceTierService,
                              SectionRepository sections,
                              EventRepository events) {
        this.performances = performances;
        this.priceTierService = priceTierService;
        this.sections = sections;
        this.events = events;
    }

    @Transactional
    public Performance create(PerformanceCommand command) {
        requireEvent(command.eventId());
        return performances.save(new Performance(
                command.eventId(),
                command.startsAt(),
                command.venueName(),
                command.venueAddress(),
                command.saleStartAt()));
    }

    @Transactional
    public Performance update(Long id, PerformanceUpdateCommand command) {
        Performance performance = require(id);
        performance.updateDetails(
                performance.getEventId(),
                command.startsAt(),
                command.venueName(),
                command.venueAddress(),
                command.saleStartAt());
        return performance;
    }

    private Performance require(Long id) {
        return performances.findById(id).orElseThrow(PerformanceService::notFound);
    }

    @Transactional(readOnly = true)
    public List<PerformanceSummary> listByEvent(Long eventId) {
        return performances.findByEventIdOrderByStartsAtAsc(eventId).stream()
                .map(this::toSummary)
                .toList();
    }

    public PerformanceSummary toSummary(Performance performance) {
        List<PriceTierView> tiers = priceTierService.listViews(performance.getId());
        SeatingMode seatingMode = sections.existsByPerformanceId(performance.getId())
                ? SeatingMode.SEATED
                : SeatingMode.GENERAL_ADMISSION;
        return new PerformanceSummary(
                performance.getId(),
                performance.getStartsAt(),
                performance.getVenueName(),
                performance.getVenueAddress(),
                performance.getSaleStartAt(),
                seatingMode,
                tiers);
    }

    private void requireEvent(Long eventId) {
        if (!events.existsById(eventId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "Performance not found");
    }
}
