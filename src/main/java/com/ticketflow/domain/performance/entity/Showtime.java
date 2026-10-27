package com.ticketflow.domain.performance.entity;

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

    public Showtime(Performance performance, LocalDateTime startsAt, LocalDateTime bookingOpenAt, int price) {
        this.performance = performance;
        this.startsAt = startsAt;
        this.bookingOpenAt = bookingOpenAt;
        this.price = price;
    }
}
