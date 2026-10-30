package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.HoldStatus;

public record HoldCancellationResponse(
        Long id,
        HoldStatus status
) {

    public static HoldCancellationResponse from(Hold hold) {
        return new HoldCancellationResponse(hold.getId(), hold.getStatus());
    }
}
