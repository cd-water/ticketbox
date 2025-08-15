package com.cdwater.ticketbox.ticket;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.order.domain.Order;
import com.cdwater.ticketbox.order.domain.OrderAttendee;
import com.cdwater.ticketbox.order.domain.OrderAttendeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketIssuer {

    private final TicketRepository tickets;
    private final OrderAttendeeRepository orderAttendees;

    public TicketIssuer(TicketRepository tickets, OrderAttendeeRepository orderAttendees) {
        this.tickets = tickets;
        this.orderAttendees = orderAttendees;
    }

    public void issue(Order order) {
        List<OrderAttendee> attendees = orderAttendees.findByOrderIdOrderByIdAsc(order.getId());
        if (attendees.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTENDEE_MISMATCH",
                    "Every ticket needs an attendee before payment");
        }
        tickets.saveAll(attendees.stream()
                .map(attendee -> new Ticket(
                        order.getId(),
                        order.getPerformanceId(),
                        attendee.getPriceTierId(),
                        attendee.getSeatId(),
                        attendee.getAttendeeName(),
                        attendee.getAttendeeIdNo()))
                .toList());
    }
}
