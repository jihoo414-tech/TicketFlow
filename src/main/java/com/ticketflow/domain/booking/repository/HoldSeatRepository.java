package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.entity.HoldSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HoldSeatRepository extends JpaRepository<HoldSeat, Long> {

    @Query("select hs from HoldSeat hs join fetch hs.hold where hs.seat.id in :seatIds")
    List<HoldSeat> findAllWithHoldBySeatIdIn(@Param("seatIds") List<Long> seatIds);
}
