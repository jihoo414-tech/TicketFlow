package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.performance.entity.Seat;

public record ReservedSeatResponse(
        Long id,
        String zoneName,
        String row,
        int number
) {

    public static ReservedSeatResponse from(Seat seat) {
        return new ReservedSeatResponse(
                seat.getId(),
                seat.getZone().getName(),
                seat.getSeatRow(),
                seat.getSeatNo()
        );
    }
}
