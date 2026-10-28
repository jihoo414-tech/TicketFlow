package com.ticketflow.domain.performance.dto;

import com.ticketflow.domain.performance.entity.Seat;

public record SeatResponse(
        Long id,
        String row,
        int number,
        int price,
        String status
) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSeatRow(),
                seat.getSeatNo(),
                seat.getShowtime().getPrice(),
                "AVAILABLE"
        );
    }
}
