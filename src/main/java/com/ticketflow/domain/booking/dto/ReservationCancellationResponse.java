package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.booking.entity.HoldStatus;
import com.ticketflow.domain.booking.entity.Reservation;

public record ReservationCancellationResponse(
        Long id,
        HoldStatus status
) {

    public static ReservationCancellationResponse from(Reservation reservation) {
        return new ReservationCancellationResponse(reservation.getId(), reservation.getHold().getStatus());
    }
}
