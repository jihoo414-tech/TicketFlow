package com.ticketflow.domain.booking.entity;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "hold")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Hold {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);
    private static final int MAX_SEAT_COUNT = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id")
    private Showtime showtime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private HoldStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    private Hold(Member member, Showtime showtime, HoldStatus status, LocalDateTime expiresAt) {
        this.member = member;
        this.showtime = showtime;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public static Hold create(Member member, Showtime showtime, List<Seat> seats, LocalDateTime now) {
        showtime.validateBookingOpen(now);
        validateSeatCount(seats);
        return new Hold(member, showtime, HoldStatus.HELD, now.plus(HOLD_DURATION));
    }

    private static void validateSeatCount(List<Seat> seats) {
        if (seats.isEmpty() || seats.size() > MAX_SEAT_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_SEAT_COUNT);
        }
    }
}
