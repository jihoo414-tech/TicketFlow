package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.booking.entity.HoldStatus;

public record OccupiedSeat(
        Long seatId,
        HoldStatus holdStatus
) {

    public SeatStatus seatStatus() {
        return holdStatus == HoldStatus.RESERVED ? SeatStatus.RESERVED : SeatStatus.HELD;
    }
}
