package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.performance.entity.Seat;

import java.time.LocalDateTime;
import java.util.List;

public record HoldResponse(
        Long id,
        List<HoldSeatResponse> seats,
        LocalDateTime expiresAt
) {

    public static HoldResponse of(Long id, List<Seat> seats, LocalDateTime expiresAt) {
        return new HoldResponse(
                id,
                seats.stream()
                        .map(HoldSeatResponse::from)
                        .toList(),
                expiresAt
        );
    }
}
