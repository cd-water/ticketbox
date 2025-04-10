package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.event.Event;
import com.cdwater.ticketbox.event.EventRepository;
import com.cdwater.ticketbox.event.EventStatus;
import com.cdwater.ticketbox.performance.Performance;
import com.cdwater.ticketbox.performance.PerformanceRepository;
import com.cdwater.ticketbox.performance.PriceTierService;
import com.cdwater.ticketbox.performance.PriceTierView;
import com.cdwater.ticketbox.performance.SeatingMode;
import com.cdwater.ticketbox.performance.Section;
import com.cdwater.ticketbox.performance.SectionRepository;
import com.cdwater.ticketbox.performance.Seat;
import com.cdwater.ticketbox.performance.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeatMapService {

    private final PerformanceRepository performances;
    private final EventRepository events;
    private final PriceTierService priceTierService;
    private final SectionRepository sections;
    private final SeatRepository seats;

    public SeatMapService(PerformanceRepository performances,
                          EventRepository events,
                          PriceTierService priceTierService,
                          SectionRepository sections,
                          SeatRepository seats) {
        this.performances = performances;
        this.events = events;
        this.priceTierService = priceTierService;
        this.sections = sections;
        this.seats = seats;
    }

    @Transactional(readOnly = true)
    public SeatMapResponse getForPublic(Long performanceId) {
        Performance performance = performances.findById(performanceId).orElseThrow(SeatMapService::notFound);
        Event event = events.findById(performance.getEventId()).orElseThrow(SeatMapService::notFound);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw notFound();
        }

        List<PriceTierView> tiers = priceTierService.listViews(performanceId);
        List<Section> sectionList = sections.findByPerformanceIdOrderByIdAsc(performanceId);

        if (sectionList.isEmpty()) {
            return new SeatMapResponse(
                    event.getId(), performanceId, SeatingMode.GENERAL_ADMISSION, List.of(), tiers);
        }

        Map<Long, PriceTierView> tierById = tiers.stream()
                .collect(Collectors.toMap(PriceTierView::id, Function.identity()));
        Map<Long, List<Seat>> seatsBySection = seats
                .findByPerformanceIdOrderBySectionIdAscRowNoAscSeatNoAsc(performanceId).stream()
                .collect(Collectors.groupingBy(Seat::getSectionId));

        List<SectionView> sectionViews = sectionList.stream()
                .map(section -> new SectionView(
                        section.getId(),
                        section.getName(),
                        tierById.get(section.getPriceTierId()),
                        section.getRowCount(),
                        section.getSeatsPerRow(),
                        seatsBySection.getOrDefault(section.getId(), List.of()).stream()
                                .map(seat -> new SeatView(
                                        seat.getId(), seat.getRowNo(), seat.getSeatNo(), SeatStatus.AVAILABLE))
                                .toList()))
                .toList();

        return new SeatMapResponse(event.getId(), performanceId, SeatingMode.SEATED, sectionViews, tiers);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "Performance not found");
    }
}
