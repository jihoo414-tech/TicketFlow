package com.ticketflow.domain.performance.repository;

import com.ticketflow.domain.performance.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findByPerformanceIdOrderByStartsAtAsc(Long performanceId);
}
