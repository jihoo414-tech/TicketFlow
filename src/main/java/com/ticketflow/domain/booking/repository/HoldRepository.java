package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.entity.Hold;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldRepository extends JpaRepository<Hold, Long> {
}
