package com.cdwater.ticketbox.waitingroom.web;

import com.cdwater.ticketbox.waitingroom.domain.WaitingRoomService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performances/{id}/waiting-room")
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;

    public WaitingRoomController(WaitingRoomService waitingRoomService) {
        this.waitingRoomService = waitingRoomService;
    }

    @PostMapping("/join")
    public WaitingRoomService.Status join(Authentication authentication, @PathVariable Long id) {
        return waitingRoomService.join(id, authentication.getName());
    }

    @GetMapping("/status")
    public WaitingRoomService.Status status(Authentication authentication, @PathVariable Long id) {
        return waitingRoomService.status(id, authentication.getName());
    }
}
