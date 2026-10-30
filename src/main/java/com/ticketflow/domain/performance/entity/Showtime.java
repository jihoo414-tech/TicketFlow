package com.ticketflow.domain.performance.entity;

import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "showtime")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Showtime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "booking_open_at")
    private LocalDateTime bookingOpenAt;

    @Column(name = "price")
    private int price;

    private Showtime(Performance performance, LocalDateTime startsAt, LocalDateTime bookingOpenAt, int price) {
        this.performance = performance;
        this.startsAt = startsAt;
        this.bookingOpenAt = bookingOpenAt;
        this.price = price;
    }

    public static Showtime create(Performance performance, LocalDateTime startsAt, LocalDateTime bookingOpenAt, int price) {
        if (!bookingOpenAt.isBefore(startsAt)) {
            throw new BusinessException(ErrorCode.BOOKING_OPEN_NOT_BEFORE_START);
        }
        return new Showtime(performance, startsAt, bookingOpenAt, price);
    }

    public void validateBookingOpen(LocalDateTime now) {
        if (now.isBefore(bookingOpenAt)) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OPEN);
        }
    }

    public void validateNotStarted(LocalDateTime now) {
        if (!now.isBefore(startsAt)) {
            throw new BusinessException(ErrorCode.SHOWTIME_ALREADY_STARTED);
        }
    }
}
