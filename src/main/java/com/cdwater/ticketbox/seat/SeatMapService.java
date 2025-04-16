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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
    private final StringRedisTemplate redis;

    public SeatMapService(PerformanceRepository performances,
                          EventRepository events,
                          PriceTierService priceTierService,
                          SectionRepository sections,
                          SeatRepository seats,
                          StringRedisTemplate redis) {
        this.performances = performances;
        this.events = events;
        this.priceTierService = priceTierService;
        this.sections = sections;
        this.seats = seats;
        this.redis = redis;
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
        List<Seat> allSeats = seats.findByPerformanceIdOrderBySectionIdAscRowNoAscSeatNoAsc(performanceId);
        Map<Long, SeatStatus> statusById = lockStatuses(performanceId, allSeats);
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

    private Map<Long, SeatStatus> lockStatuses(Long performanceId, List<Seat> allSeats) {
        Map<Long, SeatStatus> statuses = new HashMap<>(allSeats.size());
        if (allSeats.isEmpty()) {
            return statuses;
        }
        List<String> keys = allSeats.stream()
                .map(seat -> SeatLockKeys.of(performanceId, seat.getId()))
                .toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        for (int index = 0; index < allSeats.size(); index++) {
            boolean locked = values != null && values.get(index) != null;
            statuses.put(allSeats.get(index).getId(), locked ? SeatStatus.LOCKED : SeatStatus.AVAILABLE);
        }
        return statuses;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND", "Performance not found");
    }
}
