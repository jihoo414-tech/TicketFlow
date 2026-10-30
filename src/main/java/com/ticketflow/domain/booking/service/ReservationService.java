package com.ticketflow.domain.booking.service;

import com.ticketflow.domain.booking.dto.ReservationCancellationResponse;
import com.ticketflow.domain.booking.dto.ReservationResponse;
import com.ticketflow.domain.booking.entity.Hold;
import com.ticketflow.domain.booking.entity.Reservation;
import com.ticketflow.domain.booking.repository.ReservationRepository;
import com.ticketflow.domain.booking.service.SeatOccupancyLock.LockedOccupancy;
import com.ticketflow.domain.booking.service.SeatOccupancyLock.LockedSeats;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final SeatOccupancyLock seatOccupancyLock;
    private final HoldService holdService;
    private final ReservationRepository reservationRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse pay(Long memberId, Long holdId) {
        LockedOccupancy locked = seatOccupancyLock.acquireMemberAndSeats(memberId, holdService.findSeatIds(holdId));

        Hold hold = holdService.findHold(holdId);
        validateOwner(hold, memberId);

        Reservation reservation = saveReservation(hold.reserve(locked.lockedAt()));

        return ReservationResponse.from(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationCancellationResponse cancel(Long memberId, Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        Long holdId = reservation.getHold().getId();
        LockedSeats locked = seatOccupancyLock.acquireSeats(holdService.findSeatIds(holdId));

        Hold hold = holdService.findHold(holdId);
        validateReservationOwner(hold, memberId);
        hold.cancelReservation(locked.lockedAt());

        return ReservationCancellationResponse.from(reservation);
    }

    private void validateOwner(Hold hold, Long memberId) {
        if (!hold.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.HOLD_NOT_OWNED);
        }
    }

    private void validateReservationOwner(Hold hold, Long memberId) {
        if (!hold.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_OWNED);
        }
    }

    private Reservation findReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private Reservation saveReservation(Reservation reservation) {
        return reservationRepository.save(reservation);
    }
}
