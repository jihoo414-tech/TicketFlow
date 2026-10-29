package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {
    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public List<Seat> findSeats(Showtime showtime) {
        return seatRepository.findByShowtimeIdOrderBySeatRowAscSeatNoAsc(showtime.getId());
    }

    @Transactional
    public List<Seat> findLockedSeats(List<Long> seatIds) {
        return seatRepository.findAllByIdForUpdate(seatIds);
    }
}
