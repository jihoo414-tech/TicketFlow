package com.ticketflow.domain.booking.service;

import com.ticketflow.domain.booking.dto.HoldResponse;
import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.service.MemberService;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.service.SeatService;
import com.ticketflow.domain.performance.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final MemberService memberService;
    private final ShowtimeService showtimeService;
    private final SeatService seatService;
    private final HoldRepository holdRepository;
    private final Clock clock;

    @Transactional
    public HoldResponse hold(Long memberId, Long showtimeId, List<Long> seatIds) {
        Member member = memberService.findMember(memberId);


        Showtime showtime = showtimeService.findShowtime(showtimeId);

        List<Seat> seats = seatService.findSeats(seatIds);
        
        Hold hold = saveHold(member, showtime);

        return toResponse(hold, seats);
    }

    private Hold saveHold(Member member, Showtime showtime) {
        return holdRepository.save(Hold.create(member, showtime, now()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private HoldResponse toResponse(Hold hold, List<Seat> seats) {
        return HoldResponse.of(hold.getId(), seats, hold.getExpiresAt());
    }
}
