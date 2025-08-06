package com.cdwater.ticketbox.seat.web;

import com.cdwater.ticketbox.seat.domain.SeatMapResponse;
import com.cdwater.ticketbox.seat.domain.SeatMapService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
public class SeatMapController {

    private final SeatMapService seatMapService;

    public SeatMapController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @GetMapping("/{id}/seat-map")
    public SeatMapResponse seatMap(@PathVariable Long id, Authentication authentication) {
        return seatMapService.getForPublic(id, authentication == null ? null : authentication.getName());
    }
}
