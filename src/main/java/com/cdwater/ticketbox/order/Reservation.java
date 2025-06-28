package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.auth.UserAccount;
import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.performance.GaStockService;
import com.cdwater.ticketbox.performance.Performance;
import com.cdwater.ticketbox.performance.PerformanceRepository;
import com.cdwater.ticketbox.performance.PriceTier;
import com.cdwater.ticketbox.performance.PriceTierRepository;
import com.cdwater.ticketbox.performance.Seat;
import com.cdwater.ticketbox.performance.SeatRepository;
import com.cdwater.ticketbox.performance.Section;
import com.cdwater.ticketbox.performance.SectionRepository;
import com.cdwater.ticketbox.seat.SeatAvailability;
import com.cdwater.ticketbox.waitingroom.WaitingRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class Reservation {

    private final PerformanceRepository performances;
    private final SectionRepository sections;
    private final SeatRepository seats;
    private final PriceTierRepository priceTiers;
    private final SeatAvailability seatAvailability;
    private final GaStockService gaStock;
    private final WaitingRoomService waitingRoom;
    private final OrderWriter orderWriter;

    public Reservation(PerformanceRepository performances,
                       SectionRepository sections,
                       SeatRepository seats,
                       PriceTierRepository priceTiers,
                       SeatAvailability seatAvailability,
                       GaStockService gaStock,
                       WaitingRoomService waitingRoom,
                       OrderWriter orderWriter) {
        this.performances = performances;
        this.sections = sections;
        this.seats = seats;
        this.priceTiers = priceTiers;
        this.seatAvailability = seatAvailability;
        this.gaStock = gaStock;
        this.waitingRoom = waitingRoom;
        this.orderWriter = orderWriter;
    }

    public Order reserve(UserAccount user, ReservationRequest request, String idempotencyKey) {
        Performance performance = performances.findById(request.performanceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND",
                        "Performance not found"));
        waitingRoom.requireAdmission(performance.getId(), user.getUsername());

        if (request.priceTierId() != null) {
            return reserveGeneralAdmission(user, performance, request, idempotencyKey);
        }
        return reserveSeated(user, performance, request, idempotencyKey);
    }

    private Order reserveGeneralAdmission(UserAccount user, Performance performance,
                                          ReservationRequest request, String idempotencyKey) {
        if (!sections.findByPerformanceIdOrderByIdAsc(performance.getId()).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SEATED_PERFORMANCE",
                    "This performance sells specific seats");
        }
        PriceTier tier = priceTiers.findById(request.priceTierId())
                .filter(candidate -> candidate.getPerformanceId().equals(performance.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRICE_TIER_NOT_FOUND",
                        "Price tier not found"));

        int quantity = request.quantity();
        if (!gaStock.tryReserve(performance.getId(), tier.getId(), quantity, tier.getStock())) {
            throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "Not enough tickets remain");
        }

        String orderNo = newOrderNo();
        try {
            return orderWriter.persistGa(
                    user.getId(), performance.getId(), orderNo, idempotencyKey,
                    tier.getId(), quantity, tier.getPrice());
        } catch (RuntimeException exception) {
            gaStock.release(performance.getId(), tier.getId(), quantity);
            throw exception;
        }
    }

    private Order reserveSeated(UserAccount user, Performance performance,
                                ReservationRequest request, String idempotencyKey) {
        List<Section> sectionList = sections.findByPerformanceIdOrderByIdAsc(performance.getId());
        if (sectionList.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_SEATED",
                    "This performance does not sell specific seats");
        }

        List<Long> seatIds = request.seatIds();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_SEATS", "Seat ids must be unique");
        }
        List<Seat> selected = seats.findAllById(seatIds);
        if (selected.size() != seatIds.size()
                || selected.stream().anyMatch(seat -> !seat.getPerformanceId().equals(performance.getId()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND",
                    "Seat not found for this performance");
        }

        Map<Long, Section> sectionById = sectionList.stream()
                .collect(Collectors.toMap(Section::getId, Function.identity()));
        Map<Long, PriceTier> tierById = priceTiers.findByPerformanceIdOrderByPriceAsc(performance.getId()).stream()
                .collect(Collectors.toMap(PriceTier::getId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        List<OrderLine> lines = new ArrayList<>(selected.size());
        for (Seat seat : selected) {
            Section section = sectionById.get(seat.getSectionId());
            PriceTier tier = section == null ? null : tierById.get(section.getPriceTierId());
            if (tier == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PRICE_TIER_NOT_FOUND", "Price tier not found");
            }
            total = total.add(tier.getPrice());
            lines.add(new OrderLine(seat.getId(), tier.getId(), tier.getPrice()));
        }

        String orderNo = newOrderNo();
        if (!seatAvailability.lockAll(performance.getId(), seatIds, orderNo)) {
            throw new ApiException(HttpStatus.CONFLICT, "SEAT_ALREADY_LOCKED",
                    "One or more seats are already locked");
        }

        try {
            return orderWriter.persist(
                    user.getId(), performance.getId(), orderNo, idempotencyKey, total, lines);
        } catch (RuntimeException exception) {
            seatAvailability.release(performance.getId(), seatIds, orderNo);
            throw exception;
        }
    }

    /**
     * Restores inventory for a cancelled order. Must run inside the caller's transaction:
     * database changes join it, Redis changes run after commit.
     */
    public void release(Order order, List<OrderItem> items) {
        int quantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
        orderWriter.releaseQuota(order.getUserId(), order.getPerformanceId(), quantity);

        List<Long> seatIds = items.stream()
                .map(OrderItem::getSeatId)
                .filter(Objects::nonNull)
                .toList();
        List<OrderItem> generalAdmissionItems = items.stream()
                .filter(item -> item.getSeatId() == null)
                .toList();
        for (OrderItem item : generalAdmissionItems) {
            priceTiers.incrementStock(item.getPriceTierId(), item.getQuantity());
        }
        if (seatIds.isEmpty() && generalAdmissionItems.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (!seatIds.isEmpty()) {
                        seatAvailability.release(order.getPerformanceId(), seatIds, order.getOrderNo());
                    }
                    for (OrderItem item : generalAdmissionItems) {
                        gaStock.release(order.getPerformanceId(), item.getPriceTierId(), item.getQuantity());
                    }
                } catch (RuntimeException ignored) {
                    // Redis counters and locks expire or can be rebuilt; MySQL already reflects the cancellation.
                }
            }
        });
    }

    private static String newOrderNo() {
        return "ORD" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
