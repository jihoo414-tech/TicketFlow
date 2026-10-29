package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.performance.entity.Seat;

public record HoldSeatResponse(
        Long id,
        String row,
        int number
) {

    public static HoldSeatResponse from(Seat seat) {
        return new HoldSeatResponse(
                seat.getId(),
                seat.getSeatRow(),
                seat.getSeatNo()
        );
    }
}
