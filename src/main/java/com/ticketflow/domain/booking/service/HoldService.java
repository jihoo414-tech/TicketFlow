package com.ticketflow.domain.booking.service;

import com.ticketflow.domain.booking.dto.HoldCancellationResponse;
import com.ticketflow.domain.booking.dto.HoldResponse;
import com.ticketflow.domain.booking.dto.SeatResponse;
import com.ticketflow.domain.booking.dto.SeatStatus;
import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.HoldSeat;
import com.ticketflow.domain.booking.entity.HoldStatus;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.booking.service.SeatOccupancyLock.LockedOccupancy;
import com.ticketflow.domain.booking.service.SeatOccupancyLock.LockedSeats;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final ShowtimeService showtimeService;
    private final SeatService seatService;
    private final HoldRepository holdRepository;
    private final HoldSeatRepository holdSeatRepository;
    private final SeatOccupancyLock seatOccupancyLock;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldResponse hold(Long memberId, Long showtimeId, List<Long> seatIds) {

        validateNoDuplicateSeats(seatIds);

        LockedOccupancy locked = seatOccupancyLock.acquireMemberAndSeats(memberId, seatIds);


        Showtime showtime = showtimeService.findShowtime(showtimeId);

        List<Seat> seats = locked.seats();
        LocalDateTime now = locked.lockedAt();
        Hold hold = Hold.create(locked.member(), showtime, seats, now);

        validateNoActiveHold(memberId, showtimeId, now);
        validateSeatLimit(memberId, showtimeId, seats);
        validateSeatsNotHeld(seatIds, now);

        saveHold(hold);
        saveHoldSeats(hold, seats);

        return toResponse(hold, seats);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldCancellationResponse cancel(Long memberId, Long holdId) {
        LockedSeats locked = seatOccupancyLock.acquireSeats(findSeatIds(holdId));

        Hold hold = findHold(holdId);
        validateOwner(hold, memberId);
        hold.cancel(locked.lockedAt());
        return HoldCancellationResponse.from(hold);
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeats(Long showtimeId) {
        Showtime showtime = showtimeService.findShowtime(showtimeId);
        List<Seat> seats = seatService.findSeats(showtime);
        //좌석Id : 좌석 상태
        Map<Long, SeatStatus> occupiedSeatStatuses = findOccupiedSeatStatuses(showtimeId, now());
        return toSeatResponses(seats, showtime.getPrice(), occupiedSeatStatuses);
    }

    @Transactional(readOnly = true)
    public Hold findHold(Long holdId) {
        return holdRepository.findById(holdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOLD_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Long> findSeatIds(Long holdId) {
        return holdSeatRepository.findSeatIdsByHoldId(holdId);
    }

    private Map<Long, SeatStatus> findOccupiedSeatStatuses(Long showtimeId, LocalDateTime now) {
        return holdSeatRepository.findOccupyingByShowtimeId(showtimeId, HoldStatus.RESERVED, HoldStatus.HELD, now).stream()
                .collect(Collectors.toMap(holdSeat -> holdSeat.getSeat().getId(), this::statusOf));
    }

    //중복된 좌석으로 요청이 들어오는 지 확인하는데, 사용자의 의도와 다르게 중복된 요청으로 들어오는 경우가 있을수도 있어서 추후 검증 필요.
    private void validateNoDuplicateSeats(List<Long> seatIds) {
        if (Set.copyOf(seatIds).size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SEAT);
        }
    }

    private void validateOwner(Hold hold, Long memberId) {
        if (!hold.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.HOLD_NOT_OWNED);
        }
    }

    private void validateNoActiveHold(Long memberId, Long showtimeId, LocalDateTime now) {
        boolean hasActiveHold = holdRepository.findAllByMemberIdAndShowtimeId(memberId, showtimeId).stream()
                .anyMatch(hold -> hold.isHeld(now));
        if (hasActiveHold) {
            throw new BusinessException(ErrorCode.HOLD_ALREADY_EXISTS);
        }
    }

    private void validateSeatLimit(Long memberId, Long showtimeId, List<Seat> seats) {
        long reservedSeatCount = holdSeatRepository.countByMemberIdAndShowtimeIdAndStatus(
                memberId, showtimeId, HoldStatus.RESERVED);
        Hold.validateSeatLimit(reservedSeatCount, seats.size());
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

    private List<SeatResponse> toSeatResponses(List<Seat> seats, int price, Map<Long, SeatStatus> occupiedSeatStatuses) {
        return seats.stream()
                //넘겨준 좌석id : 상태 맵에 seats의 id가 있다면, 그 좌석은 이미 선점 불가, 없다면 선점 가능.
                .map(seat -> SeatResponse.of(seat, price, occupiedSeatStatuses.getOrDefault(seat.getId(), SeatStatus.AVAILABLE)))
                .toList();
    }

    private SeatStatus statusOf(HoldSeat holdSeat) {
        return holdSeat.isReserved() ? SeatStatus.RESERVED : SeatStatus.HELD;
    }
}
