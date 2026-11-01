package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.performance.dto.SeatPosition;

public record SeatResponse(
        Long id,
        String row,
        int number,
        int price,
        SeatStatus status
) {

    public static SeatResponse of(SeatPosition seat, int price, SeatStatus status) {
        return new SeatResponse(
                seat.id(),
                seat.seatRow(),
                seat.seatNo(),
                price,
                status
        );
    }
}
