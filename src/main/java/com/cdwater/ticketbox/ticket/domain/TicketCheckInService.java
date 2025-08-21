package com.cdwater.ticketbox.ticket.domain;

import com.cdwater.ticketbox.common.ApiException;
import com.cdwater.ticketbox.common.HmacSigner;
import com.cdwater.ticketbox.event.domain.Event;
import com.cdwater.ticketbox.event.domain.EventRepository;
import com.cdwater.ticketbox.performance.domain.Performance;
import com.cdwater.ticketbox.performance.domain.PerformanceRepository;
import com.cdwater.ticketbox.performance.domain.PriceTier;
import com.cdwater.ticketbox.performance.domain.PriceTierRepository;
import com.cdwater.ticketbox.performance.domain.Seat;
import com.cdwater.ticketbox.performance.domain.SeatRepository;
import com.cdwater.ticketbox.performance.domain.Section;
import com.cdwater.ticketbox.performance.domain.SectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class TicketCheckInService {

    private final TicketRepository tickets;
    private final SeatRepository seats;
    private final SectionRepository sections;
    private final PriceTierRepository priceTiers;
    private final PerformanceRepository performances;
    private final EventRepository events;
    private final String qrSecret;

    public TicketCheckInService(TicketRepository tickets,
                                SeatRepository seats,
                                SectionRepository sections,
                                PriceTierRepository priceTiers,
                                PerformanceRepository performances,
                                EventRepository events,
                                @Value("${app.ticket.qr-secret}") String qrSecret) {
        this.tickets = tickets;
        this.seats = seats;
        this.sections = sections;
        this.priceTiers = priceTiers;
        this.performances = performances;
        this.events = events;
        this.qrSecret = qrSecret;
    }

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {
        String payload = request.payload();
        if (payload == null || payload.isBlank()) {
            throw invalidTicket();
        }
        String content = payload.trim();

        // Manual fallback: an admin may type the ticket number when the camera is unavailable.
        if (content.chars().allMatch(Character::isDigit)) {
            Ticket ticket;
            try {
                ticket = tickets.findById(Long.parseLong(content)).orElseThrow(TicketCheckInService::invalidTicket);
            } catch (NumberFormatException exception) {
                throw invalidTicket();
            }
            return checkInTicket(ticket);
        }

        Optional<TicketQrCodec.Parsed> parsedOptional = TicketQrCodec.parse(content);
        if (parsedOptional.isEmpty()) {
            throw invalidTicket();
        }
        TicketQrCodec.Parsed parsed = parsedOptional.get();
        String canonical = TicketQrCodec.canonical(parsed.ticketId(), parsed.performanceId(), parsed.issuedAt());
        if (!HmacSigner.verify(qrSecret, canonical, parsed.signature())) {
            throw invalidTicket();
        }

        Ticket ticket = tickets.findById(parsed.ticketId()).orElseThrow(TicketCheckInService::invalidTicket);
        if (!ticket.getPerformanceId().equals(parsed.performanceId())
                || ticket.getIssuedAt().getEpochSecond() != parsed.issuedAt()) {
            throw invalidTicket();
        }
        return checkInTicket(ticket);
    }

    private CheckInResponse checkInTicket(Ticket ticket) {
        Instant now = Instant.now();
        int updated = tickets.checkIn(ticket.getId(), TicketStatus.CHECKED_IN, TicketStatus.ISSUED, now);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CHECKED_IN", "该票已核销");
        }
        return toResponse(ticket, now);
    }

    private CheckInResponse toResponse(Ticket ticket, Instant checkedInAt) {
        Performance performance = performances.findById(ticket.getPerformanceId()).orElse(null);
        Event event = performance == null ? null : events.findById(performance.getEventId()).orElse(null);
        Seat seat = ticket.getSeatId() == null ? null : seats.findById(ticket.getSeatId()).orElse(null);
        Section section = seat == null ? null : sections.findById(seat.getSectionId()).orElse(null);
        PriceTier tier = priceTiers.findById(ticket.getPriceTierId()).orElse(null);

        return new CheckInResponse(
                ticket.getId(),
                ticket.getAttendeeName(),
                maskIdNo(ticket.getAttendeeIdNo()),
                tier == null ? null : tier.getName(),
                section == null ? null : section.getName(),
                seat == null ? null : seat.getRowNo(),
                seat == null ? null : seat.getSeatNo(),
                TicketStatus.CHECKED_IN.name(),
                checkedInAt,
                event == null ? null : event.getTitle(),
                performance == null ? null : performance.getStartsAt(),
                performance == null ? null : performance.getVenueName());
    }

    private static String maskIdNo(String idNo) {
        if (idNo == null || idNo.length() <= 7) {
            return "****";
        }
        return idNo.substring(0, 3) + "*".repeat(idNo.length() - 7) + idNo.substring(idNo.length() - 4);
    }

    private static ApiException invalidTicket() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET", "无效的票面二维码或票号");
    }
}
