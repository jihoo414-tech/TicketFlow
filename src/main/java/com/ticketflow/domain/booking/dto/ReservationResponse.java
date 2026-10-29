package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.booking.entity.Reservation;

public record ReservationResponse(
        Long id,
        Long holdId,
        Long memberId
) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getHold().getId(),
                reservation.getMember().getId()
        );
    }
}
