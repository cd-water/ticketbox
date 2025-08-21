package com.cdwater.ticketbox.ticket.domain;

import com.cdwater.ticketbox.performance.domain.PriceTier;
import com.cdwater.ticketbox.performance.domain.PriceTierRepository;
import com.cdwater.ticketbox.performance.domain.Seat;
import com.cdwater.ticketbox.performance.domain.SeatRepository;
import com.cdwater.ticketbox.performance.domain.Section;
import com.cdwater.ticketbox.performance.domain.SectionRepository;
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
    private final PriceTierRepository priceTiers;
    private final TicketQrService qrService;

    public TicketService(TicketRepository tickets,
                         SeatRepository seats,
                         SectionRepository sections,
                         PriceTierRepository priceTiers,
                         TicketQrService qrService) {
        this.tickets = tickets;
        this.seats = seats;
        this.sections = sections;
        this.priceTiers = priceTiers;
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
        Map<Long, PriceTier> tierById = priceTiers.findAllById(orderTickets.stream()
                        .map(Ticket::getPriceTierId)
                        .toList()).stream()
                .collect(Collectors.toMap(PriceTier::getId, Function.identity()));

        return orderTickets.stream()
                .map(ticket -> {
                    Seat seat = ticket.getSeatId() == null ? null : seatById.get(ticket.getSeatId());
                    Section section = seat == null ? null : sectionById.get(seat.getSectionId());
                    PriceTier tier = tierById.get(ticket.getPriceTierId());
                    return new TicketResponse(
                            ticket.getId(),
                            ticket.getSeatId(),
                            seat == null ? null : seat.getRowNo(),
                            seat == null ? null : seat.getSeatNo(),
                            section == null ? null : section.getName(),
                            tier == null ? null : tier.getName(),
                            ticket.getAttendeeName(),
                            ticket.getAttendeeIdNo(),
                            ticket.getStatus().name(),
                            qrService.payload(ticket));
                })
                .toList();
    }
}
