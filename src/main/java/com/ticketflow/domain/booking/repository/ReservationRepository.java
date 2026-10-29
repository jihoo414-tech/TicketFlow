package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByHoldId(Long holdId);
}
