package com.ticketflow.domain.booking.service;

import com.ticketflow.domain.booking.dto.HoldCancellationResponse;
import com.ticketflow.domain.booking.dto.HoldResponse;
import com.ticketflow.domain.booking.dto.OccupiedSeat;
import com.ticketflow.domain.booking.dto.RemainingSeatCountResponse;
import com.ticketflow.domain.booking.dto.SeatResponse;
import com.ticketflow.domain.booking.dto.SeatStatus;
import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.HoldSeat;
import com.ticketflow.domain.booking.entity.HoldStatus;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.booking.service.SeatOccupancyLock.LockedOccupancy;
import com.ticketflow.domain.booking.service.SeatOccupancyLock.LockedSeats;
import com.ticketflow.domain.performance.dto.SeatPosition;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.entity.Zone;
import com.ticketflow.domain.performance.service.SeatService;
import com.ticketflow.domain.performance.service.ShowtimeService;
import com.ticketflow.domain.performance.service.ZoneService;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final ShowtimeService showtimeService;
    private final SeatService seatService;
    private final ZoneService zoneService;
    private final HoldRepository holdRepository;
    private final HoldSeatRepository holdSeatRepository;
    private final SeatOccupancyLock seatOccupancyLock;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldResponse hold(Long memberId, Long showtimeId, List<Long> seatIds) {

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
    public List<SeatResponse> getSeats(Long zoneId) {
        Zone zone = zoneService.findZone(zoneId);
        List<SeatPosition> seats = seatService.findSeatPositions(zone);
        //좌석Id : 좌석 상태
        Map<Long, SeatStatus> occupiedSeatStatuses = findOccupiedSeatStatuses(zoneId, now());
        return toSeatResponses(seats, zone.getShowtime().getPrice(), occupiedSeatStatuses);
    }

    @Transactional(readOnly = true)
    public RemainingSeatCountResponse getRemainingSeatCount(Long memberId, Long showtimeId) {
        showtimeService.validateShowtimeExists(showtimeId);
        return new RemainingSeatCountResponse(Hold.remainingSeatCount(countReservedSeats(memberId, showtimeId)));
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

    @Transactional(readOnly = true)
    public Map<Long, List<Seat>> findSeatsByHoldIds(List<Long> holdIds) {
        return holdSeatRepository.findAllWithSeatByHoldIdIn(holdIds).stream()
                .collect(Collectors.groupingBy(holdSeat -> holdSeat.getHold().getId(),
                        Collectors.mapping(HoldSeat::getSeat, Collectors.toList())));
    }

    private Map<Long, SeatStatus> findOccupiedSeatStatuses(Long zoneId, LocalDateTime now) {
        return holdSeatRepository.findOccupyingByZoneId(zoneId, HoldStatus.RESERVED, HoldStatus.HELD, now).stream()
                .collect(Collectors.toMap(OccupiedSeat::seatId, OccupiedSeat::seatStatus));
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
        Hold.validateSeatLimit(countReservedSeats(memberId, showtimeId), seats.size());
    }

    private long countReservedSeats(Long memberId, Long showtimeId) {
        return holdSeatRepository.countByMemberIdAndShowtimeIdAndStatus(memberId, showtimeId, HoldStatus.RESERVED);
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

    private List<SeatResponse> toSeatResponses(List<SeatPosition> seats, int price, Map<Long, SeatStatus> occupiedSeatStatuses) {
        return seats.stream()
                //넘겨준 좌석id : 상태 맵에 seats의 id가 있다면, 그 좌석은 이미 선점 불가, 없다면 선점 가능.
                .map(seat -> SeatResponse.of(seat, price, occupiedSeatStatuses.getOrDefault(seat.id(), SeatStatus.AVAILABLE)))
                .toList();
    }

}
