package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.event.domain.Event;
import com.cdwater.ticketbox.event.domain.EventRepository;
import com.cdwater.ticketbox.event.domain.EventStatus;
import com.cdwater.ticketbox.performance.domain.Performance;
import com.cdwater.ticketbox.performance.domain.PerformanceRepository;
import com.cdwater.ticketbox.performance.domain.PriceTierService;
import com.cdwater.ticketbox.performance.domain.PriceTierView;
import com.cdwater.ticketbox.performance.domain.SeatingMode;
import com.cdwater.ticketbox.performance.domain.Section;
import com.cdwater.ticketbox.performance.domain.SectionRepository;
import com.cdwater.ticketbox.performance.domain.Seat;
import com.cdwater.ticketbox.performance.domain.SeatRepository;
import com.cdwater.ticketbox.waitingroom.WaitingRoomService;
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
    private final SeatAvailability seatAvailability;
    private final WaitingRoomService waitingRoomService;

    public SeatMapService(PerformanceRepository performances,
                          EventRepository events,
                          PriceTierService priceTierService,
                          SectionRepository sections,
                          SeatRepository seats,
                          SeatAvailability seatAvailability,
                          WaitingRoomService waitingRoomService) {
        this.performances = performances;
        this.events = events;
        this.priceTierService = priceTierService;
        this.sections = sections;
        this.seats = seats;
        this.seatAvailability = seatAvailability;
        this.waitingRoomService = waitingRoomService;
    }

    @Transactional(readOnly = true)
    public SeatMapResponse getForPublic(Long performanceId, String username) {
        waitingRoomService.requireAdmission(performanceId, username);

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
        List<Seat> allSeats = seats.findByPerformanceIdOrderBySectionIdAscRowNoAscSeatNoAsc(performanceId);
        Map<Long, SeatStatus> statusById = seatAvailability.statuses(
                performanceId, allSeats.stream().map(Seat::getId).toList());
        Map<Long, List<Seat>> seatsBySection = allSeats.stream()
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
                                        seat.getId(),
                                        seat.getRowNo(),
                                        seat.getSeatNo(),
                                        statusById.getOrDefault(seat.getId(), SeatStatus.AVAILABLE)))
                                .toList()))
                .toList();

        return new SeatMapResponse(event.getId(), performanceId, SeatingMode.SEATED, sectionViews, tiers);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "Performance not found");
    }
}
