package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.HoldStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HoldResponse {

    public record Created(
            Long id,
            List<Seat> seats,
            LocalDateTime expiresAt
    ) {

        public static Created of(Long id, List<com.ticketflow.domain.performance.entity.Seat> seats, LocalDateTime expiresAt) {
            return new Created(
                    id,
                    seats.stream()
                            .map(Seat::from)
                            .toList(),
                    expiresAt
            );
        }
    }

    public record Cancelled(
            Long id,
            HoldStatus status
    ) {

        public static Cancelled from(Hold hold) {
            return new Cancelled(hold.getId(), hold.getStatus());
        }
    }

    public record Seat(
            Long id,
            String row,
            int number
    ) {

        public static Seat from(com.ticketflow.domain.performance.entity.Seat seat) {
            return new Seat(
                    seat.getId(),
                    seat.getSeatRow(),
                    seat.getSeatNo()
            );
        }
    }
}
