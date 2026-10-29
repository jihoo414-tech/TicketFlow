package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.performance.entity.Seat;

public record SeatResponse(
        Long id,
        String row,
        int number,
        int price,
        SeatStatus status
) {

    public static SeatResponse of(Seat seat, int price, SeatStatus status) {
        return new SeatResponse(
                seat.getId(),
                seat.getSeatRow(),
                seat.getSeatNo(),
                price,
                status
        );
    }
}
