package com.ticketflow.domain.booking.entity;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.performance.entity.Showtime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "hold")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Hold {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

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

    public static Hold create(Member member, Showtime showtime, LocalDateTime now) {
        return new Hold(member, showtime, HoldStatus.HELD, now.plus(HOLD_DURATION));
    }
}
