package com.cdwater.ticketbox.payment;

import com.cdwater.ticketbox.auth.UserAccount;
import com.cdwater.ticketbox.auth.UserAccountRepository;
import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.order.Order;
import com.cdwater.ticketbox.order.OrderAttendee;
import com.cdwater.ticketbox.order.OrderAttendeeRepository;
import com.cdwater.ticketbox.order.OrderItem;
import com.cdwater.ticketbox.order.OrderItemRepository;
import com.cdwater.ticketbox.order.OrderRepository;
import com.cdwater.ticketbox.order.OrderService;
import com.cdwater.ticketbox.order.OrderStatus;
import com.cdwater.ticketbox.seat.SeatAvailability;
import com.cdwater.ticketbox.ticket.TicketIssuer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final UserAccountRepository users;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final OrderItemRepository orderItems;
    private final OrderAttendeeRepository orderAttendees;
    private final PaymentRepository payments;
    private final TicketIssuer ticketIssuer;
    private final SeatAvailability seatAvailability;
    private final MockPaymentGateway mockGateway;
    private final StringRedisTemplate redis;

    public PaymentService(UserAccountRepository users,
                          OrderRepository orders,
                          OrderService orderService,
                          OrderItemRepository orderItems,
                          OrderAttendeeRepository orderAttendees,
                          PaymentRepository payments,
                          TicketIssuer ticketIssuer,
                          SeatAvailability seatAvailability,
                          MockPaymentGateway mockGateway,
                          StringRedisTemplate redis) {
        this.users = users;
        this.orders = orders;
        this.orderService = orderService;
        this.orderItems = orderItems;
        this.orderAttendees = orderAttendees;
        this.payments = payments;
        this.ticketIssuer = ticketIssuer;
        this.seatAvailability = seatAvailability;
        this.mockGateway = mockGateway;
        this.redis = redis;
    }

    @Transactional
    public PaymentResponse create(String username, Long orderId, PaymentRequest request) {
        UserAccount user = requireUser(username);
        Order order = orders.findByIdAndUserId(orderId, user.getId()).orElseThrow(PaymentService::orderNotFound);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "Order is not awaiting payment");
        }
        if (order.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_EXPIRED", "Order has expired");
        }

        List<OrderItem> items = orderItems.findByOrderId(orderId);
        saveAttendees(orderId, items, request.attendees());

        String lockKey = "payment:create:" + orderId;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10)))) {
            return waitForPendingPayment(orderId);
        }
        try {
            var existing = payments.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PENDING);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }

            Payment payment = payments.save(new Payment(
                    orderId,
                    "MOCK",
                    order.getTotalAmount(),
                    "MOCK-" + UUID.randomUUID().toString().replace("-", "").toUpperCase()));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mockGateway.send(payment.getId());
                }
            });
            return toResponse(payment);
        } finally {
            redis.delete(lockKey);
        }
    }

    private PaymentResponse waitForPendingPayment(Long orderId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            var existing = payments.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.PENDING);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                "A payment for this order is being created");
    }

    @Transactional
    public void handleCallback(CallbackPayload payload) {
        if (!"SUCCESS".equals(payload.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CALLBACK_STATUS",
                    "Only SUCCESS callbacks are supported");
        }
        Payment payment = payments.findByProviderTxnNo(payload.providerTxnNo())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_PENDING", "Payment is not pending");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(payload.amount());
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CALLBACK", "Invalid callback amount");
        }
        if (amount.compareTo(payment.getAmount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH",
                    "Callback amount does not match the payment");
        }

        Order order = orders.findById(payment.getOrderId()).orElseThrow(PaymentService::orderNotFound);
        Instant now = Instant.now();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            payments.updateStatusIfCurrent(payment.getId(), PaymentStatus.FAILED, PaymentStatus.PENDING);
            return;
        }
        if (!order.getExpiresAt().isAfter(now)) {
            orderService.expire(order.getId());
            return;
        }

        if (payments.updateStatusIfCurrent(payment.getId(), PaymentStatus.SUCCESS, PaymentStatus.PENDING) == 0) {
            return;
        }
        if (orders.markPaid(payment.getOrderId(), OrderStatus.PAID, OrderStatus.PENDING_PAYMENT, now, now) == 0) {
            payments.updateStatusIfCurrent(payment.getId(), PaymentStatus.FAILED, PaymentStatus.SUCCESS);
            return;
        }

        List<OrderItem> items = orderItems.findByOrderId(order.getId());
        ticketIssuer.issue(order);

        List<Long> seatIds = items.stream()
                .map(OrderItem::getSeatId)
                .filter(Objects::nonNull)
                .toList();
        if (!seatIds.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    seatAvailability.release(order.getPerformanceId(), seatIds, order.getOrderNo());
                }
            });
        }
    }

    private void saveAttendees(Long orderId, List<OrderItem> items, List<PaymentRequest.Attendee> attendees) {
        if (items.isEmpty()) {
            throw attendeeMismatch();
        }
        List<OrderAttendee> rows = new ArrayList<>();

        boolean seated = items.stream().anyMatch(item -> item.getSeatId() != null);
        if (seated) {
            if (attendees.stream().anyMatch(attendee -> attendee.seatId() == null)) {
                throw attendeeMismatch();
            }
            Map<Long, PaymentRequest.Attendee> bySeat = attendees.stream()
                    .collect(Collectors.toMap(
                            PaymentRequest.Attendee::seatId,
                            Function.identity(),
                            (left, right) -> {
                                throw attendeeMismatch();
                            }));
            if (bySeat.size() != items.size()) {
                throw attendeeMismatch();
            }
            for (OrderItem item : items) {
                PaymentRequest.Attendee attendee = item.getSeatId() == null ? null : bySeat.get(item.getSeatId());
                if (attendee == null) {
                    throw attendeeMismatch();
                }
                rows.add(new OrderAttendee(orderId, item.getPriceTierId(), item.getSeatId(),
                        attendee.name().trim(), attendee.idNo().trim()));
            }
        } else {
            if (attendees.stream().anyMatch(attendee -> attendee.seatId() != null)) {
                throw attendeeMismatch();
            }
            int total = items.stream().mapToInt(OrderItem::getQuantity).sum();
            if (attendees.size() != total) {
                throw attendeeMismatch();
            }
            int index = 0;
            for (OrderItem item : items) {
                for (int seat = 0; seat < item.getQuantity(); seat++) {
                    PaymentRequest.Attendee attendee = attendees.get(index++);
                    rows.add(new OrderAttendee(orderId, item.getPriceTierId(), null,
                            attendee.name().trim(), attendee.idNo().trim()));
                }
            }
        }

        orderAttendees.deleteForOrder(orderId);
        orderAttendees.saveAll(rows);
    }

    private static ApiException attendeeMismatch() {
        return new ApiException(HttpStatus.BAD_REQUEST, "ATTENDEE_MISMATCH",
                "Provide exactly one attendee per ticket");
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getProviderTxnNo(),
                payment.getStatus().name(),
                payment.getAmount());
    }

    private UserAccount requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private static ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
    }
}
