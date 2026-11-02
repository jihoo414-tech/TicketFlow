package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.performance.dto.SeatPosition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SeatResponse {

    public record Item(
            Long id,
            String row,
            int number,
            int price,
            SeatStatus status
    ) {

        public static Item of(SeatPosition seat, int price, SeatStatus status) {
            return new Item(
                    seat.id(),
                    seat.seatRow(),
                    seat.seatNo(),
                    price,
                    status
            );
        }
    }

    public record RemainingCount(
            int remainingSeatCount
    ) {
    }
}
