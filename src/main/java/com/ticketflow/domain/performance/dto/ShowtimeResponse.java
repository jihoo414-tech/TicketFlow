package com.ticketflow.domain.performance.dto;

import com.ticketflow.domain.performance.entity.Showtime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShowtimeResponse {

    public record Item(
            Long id,
            LocalDateTime startsAt,
            LocalDateTime bookingOpenAt,
            int price
    ) {

        public static Item from(Showtime showtime) {
            return new Item(
                    showtime.getId(),
                    showtime.getStartsAt(),
                    showtime.getBookingOpenAt(),
                    showtime.getPrice()
            );
        }
    }

    public record BookingOpen(
            Long id,
            LocalDateTime bookingOpenAt
    ) {

        public static BookingOpen from(Showtime showtime) {
            return new BookingOpen(showtime.getId(), showtime.getBookingOpenAt());
        }
    }
}
