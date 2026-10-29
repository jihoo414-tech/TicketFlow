package com.ticketflow.domain.performance.repository;

import com.ticketflow.domain.performance.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowtimeIdOrderBySeatRowAscSeatNoAsc(Long showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :seatIds order by s.id")
    List<Seat> findAllByIdForUpdate(@Param("seatIds") List<Long> seatIds);
}
