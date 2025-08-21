package com.cdwater.ticketbox.ticket.web;

import com.cdwater.ticketbox.ticket.domain.CheckInRequest;
import com.cdwater.ticketbox.ticket.domain.CheckInResponse;
import com.cdwater.ticketbox.ticket.domain.TicketCheckInService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    private final TicketCheckInService checkInService;

    public AdminTicketController(TicketCheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/check-in")
    public CheckInResponse checkIn(@RequestBody CheckInRequest request) {
        return checkInService.checkIn(request);
    }
}
