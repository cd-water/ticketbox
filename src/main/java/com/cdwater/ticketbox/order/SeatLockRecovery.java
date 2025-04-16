package com.cdwater.ticketbox.order;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SeatLockRecovery implements ApplicationRunner {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final SeatLockService seatLocks;

    public SeatLockRecovery(OrderRepository orders, OrderItemRepository orderItems, SeatLockService seatLocks) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.seatLocks = seatLocks;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Order order : orders.findByStatus(OrderStatus.PENDING_PAYMENT)) {
            List<Long> seatIds = orderItems.findByOrderId(order.getId()).stream()
                    .map(OrderItem::getSeatId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!seatIds.isEmpty()) {
                seatLocks.recover(order.getPerformanceId(), seatIds, order.getOrderNo());
            }
        }
    }
}
