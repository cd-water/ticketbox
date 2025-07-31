package com.cdwater.ticketbox.order;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.performance.domain.PriceTierRepository;
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
    private final PriceTierRepository priceTiers;
    private final Duration expiry;

    public OrderWriter(JdbcTemplate jdbc,
                       OrderRepository orders,
                       OrderItemRepository orderItems,
                       PriceTierRepository priceTiers,
                       @Value("${app.order.expiry:15m}") Duration expiry) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.orderItems = orderItems;
        this.priceTiers = priceTiers;
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

    @Transactional
    public Order persistGa(Long userId, Long performanceId, String orderNo, String idempotencyKey,
                           Long priceTierId, int quantity, BigDecimal unitPrice) {
        incrementQuota(userId, performanceId, quantity);
        if (priceTiers.decrementStock(priceTierId, quantity) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "Not enough tickets remain");
        }
        Order order = orders.save(new Order(
                orderNo, userId, performanceId, unitPrice.multiply(BigDecimal.valueOf(quantity)),
                idempotencyKey, Instant.now().plus(expiry)));
        orderItems.save(new OrderItem(order.getId(), priceTierId, null, quantity, unitPrice));
        return order;
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
