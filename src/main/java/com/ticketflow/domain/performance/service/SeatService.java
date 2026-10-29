package com.ticketflow.domain.performance.service;

import com.ticketflow.domain.performance.dto.SeatResponse;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {
    private final ShowtimeService showtimeService;
    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeats(Long showtimeId) {
        showtimeService.validateShowtimeExists(showtimeId);
        return toResponses(findSeatsOf(showtimeId));
    }

    @Transactional(readOnly = true)
    public List<Seat> findSeats(List<Long> seatIds) {
        return seatRepository.findAllById(seatIds);
    }

    private List<Seat> findSeatsOf(Long showtimeId) {
        return seatRepository.findByShowtimeIdOrderBySeatRowAscSeatNoAsc(showtimeId);
    }

    private List<SeatResponse> toResponses(List<Seat> seats) {
        return seats.stream()
                .map(SeatResponse::from)
                .toList();
    }
}
