package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.SeatPosition;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {
    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public List<SeatPosition> findSeatPositions(Showtime showtime) {
        return seatRepository.findByShowtimeIdOrderBySeatRowAscSeatNoAsc(showtime.getId());
    }

    @Transactional
    public List<Seat> findLockedSeats(List<Long> seatIds) {
        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);
        validateAllFound(seats, seatIds);
        return seats;
    }

    private void validateAllFound(List<Seat> seats, List<Long> seatIds) {
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }
    }
}
