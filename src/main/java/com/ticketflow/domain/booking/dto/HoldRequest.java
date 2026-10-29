package com.ticketflow.domain.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HoldRequest(
        @NotNull List<Long> seatIds
) {
}
