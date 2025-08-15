package com.cdwater.ticketbox.order.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderSweeper.class);

    private final OrderRepository orders;
    private final OrderService orderService;

    public OrderSweeper(OrderRepository orders, OrderService orderService) {
        this.orders = orders;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.order.sweep-interval:60000}")
    public void sweep() {
        for (Long orderId : orders.findIdsByStatusAndExpiresAtBefore(
                OrderStatus.PENDING_PAYMENT, Instant.now())) {
            try {
                orderService.expire(orderId);
            } catch (RuntimeException exception) {
                log.warn("Failed to expire order {}", orderId, exception);
            }
        }
    }
}
