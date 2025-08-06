package com.cdwater.ticketbox.seat.domain;

public record SeatView(Long id, int rowNo, int seatNo, SeatStatus status) {
}
