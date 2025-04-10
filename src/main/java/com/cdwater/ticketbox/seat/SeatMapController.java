package com.cdwater.ticketbox.seat;

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
    public SeatMapResponse seatMap(@PathVariable Long id) {
        return seatMapService.getForPublic(id);
    }
}
