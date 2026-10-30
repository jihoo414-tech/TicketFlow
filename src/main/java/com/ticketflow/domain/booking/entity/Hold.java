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
        validateSeatsInShowtime(showtime, seats);
        return new Hold(member, showtime, HoldStatus.HELD, now.plus(HOLD_DURATION));
    }

    public static void validateSeatLimit(long reservedSeatCount, int seatCount) {
        if (reservedSeatCount + seatCount > MAX_SEAT_COUNT) {
            throw new BusinessException(ErrorCode.SEAT_LIMIT_EXCEEDED);
        }
    }

    public Reservation reserve(LocalDateTime paidAt) {
        validateNotReserved();
        validateNotCancelled();
        validateHeld(paidAt);
        this.status = HoldStatus.RESERVED;
        return new Reservation(this, member);
    }

    public void cancel(LocalDateTime now) {
        validateNotReserved();
        validateNotCancelled();
        validateHeld(now);
        this.status = HoldStatus.CANCELLED;
    }

    public void cancelReservation(LocalDateTime now) {
        if (status == HoldStatus.RESERVATION_CANCELLED) {
            return;
        }
        showtime.validateNotStarted(now);
        this.status = HoldStatus.RESERVATION_CANCELLED;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    public boolean isReserved() {
        return status == HoldStatus.RESERVED;
    }

    public boolean isHeld(LocalDateTime now) {
        return status == HoldStatus.HELD && now.isBefore(expiresAt);
    }

    public boolean isOccupying(LocalDateTime now) {
        return status == HoldStatus.RESERVED || isHeld(now);
    }

    private void validateHeld(LocalDateTime now) {
        if (!isHeld(now)) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }
    }

    private void validateNotReserved() {
        if (isReserved()) {
            throw new BusinessException(ErrorCode.HOLD_ALREADY_RESERVED);
        }
    }

    private void validateNotCancelled() {
        if (status == HoldStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.HOLD_ALREADY_CANCELLED);
        }
    }

    private static void validateSeatCount(List<Seat> seats) {
        if (seats.isEmpty() || seats.size() > MAX_SEAT_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_SEAT_COUNT);
        }
    }

    private static void validateSeatsInShowtime(Showtime showtime, List<Seat> seats) {
        if (!seats.stream().allMatch(seat -> seat.belongsTo(showtime))) {
            throw new BusinessException(ErrorCode.SEAT_NOT_IN_SHOWTIME);
        }
    }
}
