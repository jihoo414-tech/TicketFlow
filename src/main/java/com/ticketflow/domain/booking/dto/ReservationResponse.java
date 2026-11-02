package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.HoldStatus;
import com.ticketflow.domain.booking.entity.Reservation;
import com.ticketflow.domain.performance.entity.Showtime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReservationResponse {

    public record Created(
            Long id,
            Long holdId,
            Long memberId
    ) {

        public static Created from(Reservation reservation) {
            return new Created(
                    reservation.getId(),
                    reservation.getHold().getId(),
                    reservation.getMember().getId()
            );
        }
    }

    public record Cancelled(
            Long id,
            HoldStatus status
    ) {

        public static Cancelled from(Reservation reservation) {
            return new Cancelled(reservation.getId(), reservation.getHold().getStatus());
        }
    }

    public record Mine(
            Long id,
            String performanceTitle,
            Long showtimeId,
            LocalDateTime startsAt,
            List<Seat> seats,
            int totalPrice,
            HoldStatus status
    ) {

        public static Mine of(Reservation reservation, List<com.ticketflow.domain.performance.entity.Seat> seats) {
            Hold hold = reservation.getHold();
            Showtime showtime = hold.getShowtime();
            return new Mine(
                    reservation.getId(),
                    showtime.getPerformance().getTitle(),
                    showtime.getId(),
                    showtime.getStartsAt(),
                    seats.stream()
                            .map(Seat::from)
                            .toList(),
                    showtime.getPrice() * seats.size(),
                    hold.getStatus()
            );
        }
    }

    public record Seat(
            Long id,
            String zoneName,
            String row,
            int number
    ) {

        public static Seat from(com.ticketflow.domain.performance.entity.Seat seat) {
            return new Seat(
                    seat.getId(),
                    seat.getZone().getName(),
                    seat.getSeatRow(),
                    seat.getSeatNo()
            );
        }
    }
}
