package com.ticketflow.domain.performance.repository;

import com.ticketflow.domain.performance.dto.SeatPosition;
import com.ticketflow.domain.performance.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    @Query("""
            select new com.ticketflow.domain.performance.dto.SeatPosition(s.id, s.seatRow, s.seatNo)
            from Seat s
            where s.zone.id = :zoneId
            order by length(s.seatRow), s.seatRow, s.seatNo
            """)
    List<SeatPosition> findPositionsByZoneId(@Param("zoneId") Long zoneId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :seatIds order by s.id")
    List<Seat> findAllByIdForUpdate(@Param("seatIds") List<Long> seatIds);
}
