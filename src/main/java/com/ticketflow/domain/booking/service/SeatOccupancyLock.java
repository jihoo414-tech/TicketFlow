package com.ticketflow.domain.booking.service;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.service.MemberService;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatOccupancyLock {

    private final MemberService memberService;
    private final SeatService seatService;
    private final Clock clock;

    public LockedOccupancy acquire(Long memberId, List<Long> seatIds) {
        Member member = memberService.findLockedMember(memberId);
        List<Seat> seats = seatService.findLockedSeats(seatIds);
        return new LockedOccupancy(member, seats, LocalDateTime.now(clock));
    }

    public record LockedOccupancy(Member member, List<Seat> seats, LocalDateTime lockedAt) {
    }
}
