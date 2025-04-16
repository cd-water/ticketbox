package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.auth.UserAccount;
import com.cdwater.ticketbox.auth.UserAccountRepository;
import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.common.PageResponse;
import com.cdwater.ticketbox.event.Event;
import com.cdwater.ticketbox.event.EventRepository;
import com.cdwater.ticketbox.performance.Performance;
import com.cdwater.ticketbox.performance.PerformanceRepository;
import com.cdwater.ticketbox.performance.PriceTier;
import com.cdwater.ticketbox.performance.PriceTierRepository;
import com.cdwater.ticketbox.performance.Seat;
import com.cdwater.ticketbox.performance.SeatRepository;
import com.cdwater.ticketbox.performance.Section;
import com.cdwater.ticketbox.performance.SectionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    public record CreateResult(OrderResponse order, boolean created) {
    }

    private final UserAccountRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PerformanceRepository performances;
    private final EventRepository events;
    private final SectionRepository sections;
    private final SeatRepository seats;
    private final PriceTierRepository priceTiers;
    private final SeatLockService seatLocks;
    private final IdempotencyLockService idempotencyLocks;
    private final OrderWriter orderWriter;

    public OrderService(UserAccountRepository users,
                        OrderRepository orders,
                        OrderItemRepository orderItems,
                        PerformanceRepository performances,
                        EventRepository events,
                        SectionRepository sections,
                        SeatRepository seats,
                        PriceTierRepository priceTiers,
                        SeatLockService seatLocks,
                        IdempotencyLockService idempotencyLocks,
                        OrderWriter orderWriter) {
        this.users = users;
        this.orders = orders;
        this.orderItems = orderItems;
        this.performances = performances;
        this.events = events;
        this.sections = sections;
        this.seats = seats;
        this.priceTiers = priceTiers;
        this.seatLocks = seatLocks;
        this.idempotencyLocks = idempotencyLocks;
        this.orderWriter = orderWriter;
    }

    public CreateResult create(String username, String idempotencyKey, CreateOrderCommand command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "The Idempotency-Key header is required");
        }
        UserAccount user = requireUser(username);

        Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return new CreateResult(toResponse(existing.get()), false);
        }
        boolean acquired = false;
        for (int attempt = 0; attempt < 50 && !acquired; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
                existing = orders.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);
                if (existing.isPresent()) {
                    return new CreateResult(toResponse(existing.get()), false);
                }
            }
            acquired = idempotencyLocks.tryAcquire(user.getId(), idempotencyKey);
        }
        if (!acquired) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_IN_PROGRESS",
                    "A request with this idempotency key is still in progress");
        }

        try {
            existing = orders.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return new CreateResult(toResponse(existing.get()), false);
            }
            return createLocked(user, idempotencyKey, command);
        } finally {
            idempotencyLocks.release(user.getId(), idempotencyKey);
        }
    }

    private CreateResult createLocked(UserAccount user, String idempotencyKey, CreateOrderCommand command) {
        Performance performance = performances.findById(command.performanceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PERFORMANCE_NOT_FOUND",
                        "Performance not found"));
        List<Section> sectionList = sections.findByPerformanceIdOrderByIdAsc(performance.getId());
        if (sectionList.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_SEATED",
                    "This performance does not sell specific seats");
        }

        List<Long> seatIds = command.seatIds();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_SEATS", "Seat ids must be unique");
        }
        List<Seat> selected = seats.findAllById(seatIds);
        if (selected.size() != seatIds.size()
                || selected.stream().anyMatch(seat -> !seat.getPerformanceId().equals(performance.getId()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SEAT_NOT_FOUND", "Seat not found for this performance");
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

        String orderNo = "ORD" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        if (!seatLocks.lockAll(performance.getId(), seatIds, orderNo)) {
            throw new ApiException(HttpStatus.CONFLICT, "SEAT_ALREADY_LOCKED",
                    "One or more seats are already locked");
        }

        Order order;
        try {
            order = orderWriter.persist(
                    user.getId(), performance.getId(), orderNo, idempotencyKey, total, lines);
        } catch (DataIntegrityViolationException exception) {
            seatLocks.release(performance.getId(), seatIds, orderNo);
            return orders.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey)
                    .map(saved -> new CreateResult(toResponse(saved), false))
                    .orElseThrow(() -> exception);
        } catch (RuntimeException exception) {
            seatLocks.release(performance.getId(), seatIds, orderNo);
            throw exception;
        }
        // The order is committed; mapping failures must not release its seat locks.
        return new CreateResult(toResponse(order), true);
    }

    @Transactional
    public OrderResponse cancel(String username, Long orderId) {
        UserAccount user = requireUser(username);
        Order order = orders.findByIdAndUserId(orderId, user.getId()).orElseThrow(OrderService::orderNotFound);

        int cancelled = orders.cancelIfPending(orderId, OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT);
        if (cancelled == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "Order is not awaiting payment");
        }
        releaseReservations(order, orderItems.findByOrderId(orderId));
        return toResponse(orders.findById(orderId).orElseThrow(OrderService::orderNotFound));
    }

    @Transactional
    public void expire(Long orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        if (orders.cancelIfPending(orderId, OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT) == 0) {
            return;
        }
        releaseReservations(order, orderItems.findByOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(String username, int page, int size) {
        UserAccount user = requireUser(username);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(orders.findByUserId(user.getId(), pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String username, Long orderId) {
        UserAccount user = requireUser(username);
        return orders.findByIdAndUserId(orderId, user.getId())
                .map(this::toResponse)
                .orElseThrow(OrderService::orderNotFound);
    }

    private void releaseReservations(Order order, List<OrderItem> items) {
        int quantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
        orderWriter.releaseQuota(order.getUserId(), order.getPerformanceId(), quantity);

        List<Long> seatIds = items.stream()
                .map(OrderItem::getSeatId)
                .filter(Objects::nonNull)
                .toList();
        if (seatIds.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    seatLocks.release(order.getPerformanceId(), seatIds, order.getOrderNo());
                } catch (RuntimeException ignored) {
                    // Locks expire on their own; the order is already cancelled in MySQL.
                }
            }
        });
    }

    private OrderResponse toResponse(Order order) {
        Performance performance = performances.findById(order.getPerformanceId()).orElse(null);
        Event event = performance == null ? null : events.findById(performance.getEventId()).orElse(null);
        List<OrderItem> items = orderItems.findByOrderId(order.getId());

        Set<Long> seatIds = items.stream()
                .map(OrderItem::getSeatId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Seat> seatById = seats.findAllById(seatIds).stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));
        Set<Long> sectionIds = seatById.values().stream()
                .map(Seat::getSectionId)
                .collect(Collectors.toSet());
        Map<Long, Section> sectionById = sections.findAllById(sectionIds).stream()
                .collect(Collectors.toMap(Section::getId, Function.identity()));
        Set<Long> tierIds = items.stream().map(OrderItem::getPriceTierId).collect(Collectors.toSet());
        Map<Long, PriceTier> tierById = priceTiers.findAllById(tierIds).stream()
                .collect(Collectors.toMap(PriceTier::getId, Function.identity()));

        List<OrderItemResponse> itemViews = items.stream()
                .map(item -> {
                    Seat seat = item.getSeatId() == null ? null : seatById.get(item.getSeatId());
                    Section section = seat == null ? null : sectionById.get(seat.getSectionId());
                    PriceTier tier = tierById.get(item.getPriceTierId());
                    return new OrderItemResponse(
                            item.getSeatId(),
                            seat == null ? null : seat.getRowNo(),
                            seat == null ? null : seat.getSeatNo(),
                            section == null ? null : section.getName(),
                            tier == null ? null : tier.getName(),
                            item.getUnitPrice());
                })
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                event == null ? null : event.getId(),
                event == null ? null : event.getTitle(),
                performance == null ? null : performance.getStartsAt(),
                performance == null ? null : performance.getVenueName(),
                itemViews);
    }

    private UserAccount requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private static ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
    }
}
