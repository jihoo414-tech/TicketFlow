package com.ticketflow.domain.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

public record HoldRequest(
        @NotEmpty @UniqueElements List<@NotNull Long> seatIds
) {
}
