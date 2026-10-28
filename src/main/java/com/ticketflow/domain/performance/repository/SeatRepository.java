package com.ticketflow.domain.performance.repository;

import com.ticketflow.domain.performance.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowtimeIdOrderBySeatRowAscSeatNoAsc(Long showtimeId);
}
