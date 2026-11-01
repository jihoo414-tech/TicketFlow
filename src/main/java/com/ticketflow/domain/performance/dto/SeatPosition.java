package com.ticketflow.domain.performance.dto;

public record SeatPosition(
        Long id,
        String seatRow,
        int seatNo
) {
}
