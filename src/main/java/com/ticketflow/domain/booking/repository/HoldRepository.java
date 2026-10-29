package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.entity.Hold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    List<Hold> findAllByMemberIdAndShowtimeId(Long memberId, Long showtimeId);
}
