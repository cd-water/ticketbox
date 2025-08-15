package com.cdwater.ticketbox.order.domain;

import com.cdwater.ticketbox.auth.domain.UserAccount;
import com.cdwater.ticketbox.auth.domain.UserAccountRepository;
import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.common.PageResponse;
import com.cdwater.ticketbox.event.domain.Event;
import com.cdwater.ticketbox.event.domain.EventRepository;
import com.cdwater.ticketbox.performance.domain.Performance;
import com.cdwater.ticketbox.performance.domain.PerformanceRepository;
import com.cdwater.ticketbox.performance.domain.PriceTier;
import com.cdwater.ticketbox.performance.domain.PriceTierRepository;
import com.cdwater.ticketbox.performance.domain.Seat;
import com.cdwater.ticketbox.performance.domain.SeatRepository;
import com.cdwater.ticketbox.performance.domain.Section;
import com.cdwater.ticketbox.performance.domain.SectionRepository;
import com.cdwater.ticketbox.payment.PaymentRepository;
import com.cdwater.ticketbox.payment.PaymentStatus;
import com.cdwater.ticketbox.ticket.TicketService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    public record CreateResult(OrderResponse order, boolean created) {
    }

    public record AdminOrderResponse(String username, OrderResponse order) {
    }

    public record AdminOrderSummary(
            String username,
            Long id,
            String orderNo,
            String status,
            BigDecimal totalAmount,
            Instant createdAt,
            Long eventId,
            String eventTitle,
            Instant startsAt,
            String venueName) {
    }

    private final UserAccountRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PerformanceRepository performances;
    private final EventRepository events;
    private final SectionRepository sections;
    private final SeatRepository seats;
    private final PriceTierRepository priceTiers;
    private final IdempotencyLockService idempotencyLocks;
    private final TicketService ticketService;
    private final PaymentRepository payments;
    private final Reservation reservation;

    public OrderService(UserAccountRepository users,
                        OrderRepository orders,
                        OrderItemRepository orderItems,
                        PerformanceRepository performances,
                        EventRepository events,
                        SectionRepository sections,
                        SeatRepository seats,
                        PriceTierRepository priceTiers,
                        IdempotencyLockService idempotencyLocks,
                        TicketService ticketService,
                        PaymentRepository payments,
                        Reservation reservation) {
        this.users = users;
        this.orders = orders;
        this.orderItems = orderItems;
        this.performances = performances;
        this.events = events;
        this.sections = sections;
        this.seats = seats;
        this.priceTiers = priceTiers;
        this.idempotencyLocks = idempotencyLocks;
        this.ticketService = ticketService;
        this.payments = payments;
        this.reservation = reservation;
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
        ReservationRequest request = command.priceTierId() != null
                ? new ReservationRequest(command.performanceId(), null, command.priceTierId(), command.quantity())
                : new ReservationRequest(command.performanceId(), command.seatIds(), null, null);
        try {
            Order order = reservation.reserve(user, request, idempotencyKey);
            return new CreateResult(toResponse(order), true);
        } catch (DataIntegrityViolationException exception) {
            return orders.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey)
                    .map(saved -> new CreateResult(toResponse(saved), false))
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional
    public OrderResponse cancel(String username, Long orderId) {
        UserAccount user = requireUser(username);
        Order order = orders.findByIdAndUserId(orderId, user.getId()).orElseThrow(OrderService::orderNotFound);
        if (!cancelAndRelease(order)) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "Order is not awaiting payment");
        }
        return toResponse(orders.findById(orderId).orElseThrow(OrderService::orderNotFound));
    }

    @Transactional
    public void expire(Long orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order != null) {
            cancelAndRelease(order);
        }
    }

    private boolean cancelAndRelease(Order order) {
        if (orders.cancelIfPending(order.getId(), OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT) == 0) {
            return false;
        }
        payments.expirePendingForOrder(order.getId(), PaymentStatus.EXPIRED, PaymentStatus.PENDING);
        reservation.release(order, orderItems.findByOrderId(order.getId()));
        return true;
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

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummary> listAdmin(Long performanceId, OrderStatus status,
                                                     int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result;
        if (performanceId != null && status != null) {
            result = orders.findByPerformanceIdAndStatus(performanceId, status, pageable);
        } else if (performanceId != null) {
            result = orders.findByPerformanceId(performanceId, pageable);
        } else if (status != null) {
            result = orders.findByStatus(status, pageable);
        } else {
            result = orders.findAll(pageable);
        }
        return PageResponse.from(result, this::toAdminSummary);
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse getAdmin(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow(OrderService::orderNotFound);
        return toAdminResponse(order);
    }

    private AdminOrderResponse toAdminResponse(Order order) {
        String username = users.findById(order.getUserId())
                .map(UserAccount::getUsername)
                .orElse("unknown");
        return new AdminOrderResponse(username, toResponse(order));
    }

    private AdminOrderSummary toAdminSummary(Order order) {
        String username = users.findById(order.getUserId())
                .map(UserAccount::getUsername)
                .orElse("unknown");
        Performance performance = performances.findById(order.getPerformanceId()).orElse(null);
        Event event = performance == null ? null : events.findById(performance.getEventId()).orElse(null);
        return new AdminOrderSummary(
                username,
                order.getId(),
                order.getOrderNo(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                event == null ? null : event.getId(),
                event == null ? null : event.getTitle(),
                performance == null ? null : performance.getStartsAt(),
                performance == null ? null : performance.getVenueName());
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
                            item.getQuantity(),
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
                itemViews,
                ticketService.listForOrder(order.getId()));
    }

    private UserAccount requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private static ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
    }
}
