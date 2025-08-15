package com.cdwater.ticketbox.order.domain;

import com.cdwater.ticketbox.support.ApiIntegrationTest;
import com.cdwater.ticketbox.auth.domain.UserAccount;
import com.cdwater.ticketbox.auth.domain.UserAccountRepository;
import com.cdwater.ticketbox.performance.domain.PriceTierRepository;
import com.cdwater.ticketbox.seat.domain.SeatAvailability;
import com.cdwater.ticketbox.seat.domain.SeatStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationTest extends ApiIntegrationTest {

    @Autowired
    private Reservation reservation;

    @Autowired
    private SeatAvailability seatAvailability;

    @Autowired
    private OrderItemRepository orderItems;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PriceTierRepository priceTiers;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void reservesAndReleasesSeatedAndGeneralAdmissionInventory() throws Exception {
        String admin = adminToken();
        userToken("reservation_user");
        UserAccount user = users.findByUsername("reservation_user").orElseThrow();

        SeatedPerformance seated = seatedPerformance(admin, "预留模块座位测试");
        List<Long> chosen = seated.seatIds().subList(0, 2);
        Order seatedOrder = reservation.reserve(user,
                new ReservationRequest(seated.performanceId(), chosen, null, null), "res-seated-key");
        assertThat(seatAvailability.statuses(seated.performanceId(), chosen).values())
                .containsOnly(SeatStatus.LOCKED);
        assertThat(quotaOf(user.getId(), seated.performanceId())).isEqualTo(2);

        transactions.executeWithoutResult(
                status -> reservation.release(seatedOrder, orderItems.findByOrderId(seatedOrder.getId())));
        assertThat(seatAvailability.statuses(seated.performanceId(), chosen).values())
                .containsOnly(SeatStatus.AVAILABLE);
        assertThat(quotaOf(user.getId(), seated.performanceId())).isZero();

        GaPerformance ga = gaPerformance(admin, "预留模块普通票测试", 5);
        Order gaOrder = reservation.reserve(user,
                new ReservationRequest(ga.performanceId(), null, ga.tierId(), 2), "res-ga-key");
        assertThat(dbStockOf(ga.tierId())).isEqualTo(3);

        transactions.executeWithoutResult(
                status -> reservation.release(gaOrder, orderItems.findByOrderId(gaOrder.getId())));
        assertThat(dbStockOf(ga.tierId())).isEqualTo(5);
    }

    private int quotaOf(long userId, long performanceId) {
        Integer used = jdbc.queryForObject(
                "SELECT used_quantity FROM purchase_quota WHERE user_id = ? AND performance_id = ?",
                Integer.class, userId, performanceId);
        return used == null ? 0 : used;
    }

    private int dbStockOf(long tierId) {
        return priceTiers.findById(tierId).orElseThrow().getStock();
    }
}
