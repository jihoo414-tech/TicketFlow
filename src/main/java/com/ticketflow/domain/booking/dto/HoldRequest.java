package com.ticketflow.domain.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HoldRequest {

    public record Create(
            @NotEmpty(message = "INVALID_SEAT_COUNT")
            @UniqueElements(message = "DUPLICATE_SEAT")
            List<@NotNull(message = "INVALID_SEAT_ID") Long> seatIds
    ) {
    }
}
