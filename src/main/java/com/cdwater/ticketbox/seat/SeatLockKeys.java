package com.cdwater.ticketbox.seat;

public final class SeatLockKeys {

    private SeatLockKeys() {
    }

    public static String of(Long performanceId, Long seatId) {
        return "seat:" + performanceId + ":" + seatId;
    }
}
