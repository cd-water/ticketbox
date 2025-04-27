package com.cdwater.ticketbox.seat;

import com.cdwater.ticketbox.waitingroom.WaitingRoomService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances")
public class SeatMapController {

    private final SeatMapService seatMapService;
    private final WaitingRoomService waitingRoomService;

    public SeatMapController(SeatMapService seatMapService, WaitingRoomService waitingRoomService) {
        this.seatMapService = seatMapService;
        this.waitingRoomService = waitingRoomService;
    }

    @GetMapping("/{id}/seat-map")
    public SeatMapResponse seatMap(@PathVariable Long id, Authentication authentication) {
        waitingRoomService.requireAdmission(id, authentication == null ? null : authentication.getName());
        return seatMapService.getForPublic(id);
    }
}
