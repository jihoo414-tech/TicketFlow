package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByHoldId(Long holdId);

    @Query("""
            select r from Reservation r
            join fetch r.hold h
            join fetch h.showtime s
            join fetch s.performance
            where r.member.id = :memberId
            order by r.id desc
            """)
    List<Reservation> findAllWithShowtimeByMemberId(@Param("memberId") Long memberId);
}
