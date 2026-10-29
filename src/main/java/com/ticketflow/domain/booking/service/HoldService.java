package com.ticketflow.domain.booking.service;

import com.ticketflow.domain.booking.dto.HoldResponse;
import com.ticketflow.domain.booking.dto.SeatResponse;
import com.ticketflow.domain.booking.dto.SeatStatus;
import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.HoldSeat;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.service.MemberService;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.service.SeatService;
import com.ticketflow.domain.performance.service.ShowtimeService;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final MemberService memberService;
    private final ShowtimeService showtimeService;
    private final SeatService seatService;
    private final HoldRepository holdRepository;
    private final HoldSeatRepository holdSeatRepository;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldResponse hold(Long memberId, Long showtimeId, List<Long> seatIds) {
        Member member = memberService.findMember(memberId);

        Showtime showtime = showtimeService.findShowtime(showtimeId);

        List<Seat> seats = seatService.findLockedSeats(seatIds);
        LocalDateTime now = now();
        Hold hold = Hold.create(member, showtime, seats, now);

        validateNoActiveHold(memberId, showtimeId, now);
        validateSeatsNotHeld(seatIds, now);

        saveHold(hold);
        saveHoldSeats(hold, seats);

        return toResponse(hold, seats);
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeats(Long showtimeId) {
        Showtime showtime = showtimeService.findShowtime(showtimeId);
        List<Seat> seats = seatService.findSeats(showtime);
        Set<Long> occupiedSeatIds = findOccupiedSeatIds(showtimeId, now());
        return toSeatResponses(seats, showtime.getPrice(), occupiedSeatIds);
    }

    private Set<Long> findOccupiedSeatIds(Long showtimeId, LocalDateTime now) {
        return holdSeatRepository.findAllWithHoldByShowtimeId(showtimeId).stream()
                .filter(holdSeat -> holdSeat.isOccupying(now))
                .map(holdSeat -> holdSeat.getSeat().getId())
                .collect(Collectors.toSet());
    }

    private void validateNoActiveHold(Long memberId, Long showtimeId, LocalDateTime now) {
        boolean hasActiveHold = holdRepository.findAllByMemberIdAndShowtimeId(memberId, showtimeId).stream()
                .anyMatch(hold -> hold.isOccupying(now));
        if (hasActiveHold) {
            throw new BusinessException(ErrorCode.HOLD_ALREADY_EXISTS);
        }
    }

    private void validateSeatsNotHeld(List<Long> seatIds, LocalDateTime now) {
        boolean anyOccupied = holdSeatRepository.findAllWithHoldBySeatIdIn(seatIds).stream()
                .anyMatch(holdSeat -> holdSeat.isOccupying(now));
        if (anyOccupied) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
        }
    }

    private void saveHold(Hold hold) {
        holdRepository.save(hold);
    }

    private void saveHoldSeats(Hold hold, List<Seat> seats) {
        holdSeatRepository.saveAll(seats.stream()
                .map(seat -> new HoldSeat(hold, seat))
                .toList());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private HoldResponse toResponse(Hold hold, List<Seat> seats) {
        return HoldResponse.of(hold.getId(), seats, hold.getExpiresAt());
    }

    private List<SeatResponse> toSeatResponses(List<Seat> seats, int price, Set<Long> occupiedSeatIds) {
        return seats.stream()
                .map(seat -> SeatResponse.of(seat, price, statusOf(seat, occupiedSeatIds)))
                .toList();
    }

    private SeatStatus statusOf(Seat seat, Set<Long> occupiedSeatIds) {
        return occupiedSeatIds.contains(seat.getId()) ? SeatStatus.HELD : SeatStatus.AVAILABLE;
    }
}
