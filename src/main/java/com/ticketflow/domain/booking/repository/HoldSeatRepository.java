package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.entity.HoldSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldSeatRepository extends JpaRepository<HoldSeat, Long> {

    boolean existsBySeatIdIn(List<Long> seatIds);
}
