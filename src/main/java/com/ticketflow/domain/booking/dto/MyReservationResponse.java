package com.ticketflow.domain.booking.dto;

import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.HoldStatus;
import com.ticketflow.domain.booking.entity.Reservation;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;

import java.time.LocalDateTime;
import java.util.List;

public record MyReservationResponse(
        Long id,
        String performanceTitle,
        Long showtimeId,
        LocalDateTime startsAt,
        List<HoldSeatResponse> seats,
        int totalPrice,
        HoldStatus status
) {

    public static MyReservationResponse of(Reservation reservation, List<Seat> seats) {
        Hold hold = reservation.getHold();
        Showtime showtime = hold.getShowtime();
        return new MyReservationResponse(
                reservation.getId(),
                showtime.getPerformance().getTitle(),
                showtime.getId(),
                showtime.getStartsAt(),
                seats.stream()
                        .map(HoldSeatResponse::from)
                        .toList(),
                showtime.getPrice() * seats.size(),
                hold.getStatus()
        );
    }
}
