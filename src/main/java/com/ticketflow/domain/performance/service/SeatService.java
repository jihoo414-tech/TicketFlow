package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.SeatPosition;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Zone;
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
    public List<SeatPosition> findSeatPositions(Zone zone) {
        return seatRepository.findPositionsByZoneId(zone.getId());
    }

    @Transactional
    public List<Seat> findLockedSeats(List<Long> seatIds) {
        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);
        validateAllFound(seats, seatIds);
        return seats;
    }

    //없는 좌석이 섞여있는 것을 검증
    private void validateAllFound(List<Seat> seats, List<Long> seatIds) {
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }
    }
}
