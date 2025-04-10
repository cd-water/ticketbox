package com.cdwater.ticketbox.seat;

public record SeatView(Long id, int rowNo, int seatNo, SeatStatus status) {
}
