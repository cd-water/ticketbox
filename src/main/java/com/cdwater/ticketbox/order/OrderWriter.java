package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OrderWriter {

    static final int MAX_TICKETS_PER_PERFORMANCE = 4;

    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final Duration expiry;

    public OrderWriter(JdbcTemplate jdbc,
                       OrderRepository orders,
                       OrderItemRepository orderItems,
                       @Value("${app.order.expiry:15m}") Duration expiry) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.orderItems = orderItems;
        this.expiry = expiry;
    }

    @Transactional
    public Order persist(Long userId, Long performanceId, String orderNo, String idempotencyKey,
                         BigDecimal total, List<OrderLine> lines) {
        int quantity = lines.size();
        incrementQuota(userId, performanceId, quantity);

        Order order = orders.save(new Order(
                orderNo, userId, performanceId, total, idempotencyKey, Instant.now().plus(expiry)));
        orderItems.saveAll(lines.stream()
                .map(line -> new OrderItem(order.getId(), line.priceTierId(), line.seatId(), 1, line.unitPrice()))
                .toList());
        return order;
    }

    private void incrementQuota(Long userId, Long performanceId, int quantity) {
        String conditionalUpdate = """
                UPDATE purchase_quota
                SET used_quantity = used_quantity + ?
                WHERE user_id = ? AND performance_id = ? AND used_quantity + ? <= ?
                """;
        int updated = jdbc.update(conditionalUpdate,
                quantity, userId, performanceId, quantity, MAX_TICKETS_PER_PERFORMANCE);
        if (updated > 0) {
            return;
        }

        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_quota WHERE user_id = ? AND performance_id = ?",
                Integer.class, userId, performanceId);
        if (existing != null && existing > 0) {
            throw limitExceeded();
        }

        try {
            jdbc.update("""
                            INSERT INTO purchase_quota (user_id, performance_id, used_quantity)
                            VALUES (?, ?, ?)
                            """,
                    userId, performanceId, quantity);
        } catch (DuplicateKeyException exception) {
            int retried = jdbc.update(conditionalUpdate,
                    quantity, userId, performanceId, quantity, MAX_TICKETS_PER_PERFORMANCE);
            if (retried == 0) {
                throw limitExceeded();
            }
        }
    }

    private static ApiException limitExceeded() {
        return new ApiException(HttpStatus.CONFLICT, "PURCHASE_LIMIT_EXCEEDED",
                "Each user may buy at most 4 tickets per performance");
    }

    public void releaseQuota(Long userId, Long performanceId, int quantity) {
        jdbc.update("""
                        UPDATE purchase_quota
                        SET used_quantity = GREATEST(used_quantity - ?, 0)
                        WHERE user_id = ? AND performance_id = ?
                        """,
                quantity, userId, performanceId);
    }
}
