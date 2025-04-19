package com.cdwater.ticketbox.ticket;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.order.Order;
import com.cdwater.ticketbox.order.OrderItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketIssuer {

    private final TicketRepository tickets;

    public TicketIssuer(TicketRepository tickets) {
        this.tickets = tickets;
    }

    public void issue(Order order, List<OrderItem> items) {
        List<Ticket> issued = items.stream()
                .map(item -> {
                    if (item.getAttendeeName() == null || item.getAttendeeIdNo() == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "ATTENDEE_MISMATCH",
                                "Every seat needs an attendee before payment");
                    }
                    return new Ticket(
                            order.getId(),
                            order.getPerformanceId(),
                            item.getPriceTierId(),
                            item.getSeatId(),
                            item.getAttendeeName(),
                            item.getAttendeeIdNo());
                })
                .toList();
        tickets.saveAll(issued);
    }
}
