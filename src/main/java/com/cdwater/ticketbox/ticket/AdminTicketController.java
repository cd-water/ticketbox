package com.cdwater.ticketbox.ticket;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    public record CheckInRequest(String payload) {
    }

    private final TicketCheckInService checkInService;

    public AdminTicketController(TicketCheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/check-in")
    public CheckInResponse checkIn(@RequestBody CheckInRequest request) {
        return checkInService.checkIn(request.payload());
    }
}
