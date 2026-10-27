package com.ticketflow.domain.performance.dto;

import com.ticketflow.domain.performance.entity.Showtime;

import java.time.LocalDateTime;

public record ShowtimeResponse(
        Long id,
        LocalDateTime startsAt,
        LocalDateTime bookingOpenAt,
        int price
) {

    public static ShowtimeResponse from(Showtime showtime) {
        return new ShowtimeResponse(
                showtime.getId(),
                showtime.getStartsAt(),
                showtime.getBookingOpenAt(),
                showtime.getPrice()
        );
    }
}
