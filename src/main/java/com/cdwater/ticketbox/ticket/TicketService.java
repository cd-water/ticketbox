package com.cdwater.ticketbox.ticket;

import com.cdwater.ticketbox.performance.Seat;
import com.cdwater.ticketbox.performance.SeatRepository;
import com.cdwater.ticketbox.performance.Section;
import com.cdwater.ticketbox.performance.SectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final SeatRepository seats;
    private final SectionRepository sections;
    private final TicketQrService qrService;

    public TicketService(TicketRepository tickets,
                         SeatRepository seats,
                         SectionRepository sections,
                         TicketQrService qrService) {
        this.tickets = tickets;
        this.seats = seats;
        this.sections = sections;
        this.qrService = qrService;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listForOrder(Long orderId) {
        List<Ticket> orderTickets = tickets.findByOrderIdOrderByIdAsc(orderId);
        if (orderTickets.isEmpty()) {
            return List.of();
        }

        Map<Long, Seat> seatById = seats.findAllById(orderTickets.stream()
                        .map(Ticket::getSeatId)
                        .filter(seatId -> seatId != null)
                        .toList()).stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));
        Map<Long, Section> sectionById = sections.findAllById(seatById.values().stream()
                        .map(Seat::getSectionId)
                        .toList()).stream()
                .collect(Collectors.toMap(Section::getId, Function.identity()));

        return orderTickets.stream()
                .map(ticket -> {
                    Seat seat = ticket.getSeatId() == null ? null : seatById.get(ticket.getSeatId());
                    Section section = seat == null ? null : sectionById.get(seat.getSectionId());
                    return new TicketResponse(
                            ticket.getId(),
                            ticket.getSeatId(),
                            seat == null ? null : seat.getRowNo(),
                            seat == null ? null : seat.getSeatNo(),
                            section == null ? null : section.getName(),
                            ticket.getAttendeeName(),
                            ticket.getAttendeeIdNo(),
                            ticket.getStatus().name(),
                            qrService.payload(ticket));
                })
                .toList();
    }
}
