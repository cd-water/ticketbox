package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.seat.domain.SeatAvailability;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SeatLockRecovery implements ApplicationRunner {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final SeatAvailability seatAvailability;

    public SeatLockRecovery(OrderRepository orders, OrderItemRepository orderItems, SeatAvailability seatAvailability) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.seatAvailability = seatAvailability;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Order order : orders.findByStatus(OrderStatus.PENDING_PAYMENT)) {
            List<Long> seatIds = orderItems.findByOrderId(order.getId()).stream()
                    .map(OrderItem::getSeatId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!seatIds.isEmpty()) {
                seatAvailability.recover(order.getPerformanceId(), seatIds, order.getOrderNo());
            }
        }
    }
}
