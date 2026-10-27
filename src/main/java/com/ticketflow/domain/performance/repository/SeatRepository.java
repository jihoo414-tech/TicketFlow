package com.ticketflow.domain.performance.repository;

import com.ticketflow.domain.performance.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
