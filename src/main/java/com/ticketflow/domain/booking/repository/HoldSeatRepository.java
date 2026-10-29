package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.entity.HoldSeat;
import com.ticketflow.domain.booking.entity.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HoldSeatRepository extends JpaRepository<HoldSeat, Long> {

    @Query("select hs from HoldSeat hs join fetch hs.hold where hs.seat.id in :seatIds")
    List<HoldSeat> findAllWithHoldBySeatIdIn(@Param("seatIds") List<Long> seatIds);

    @Query("select hs from HoldSeat hs join fetch hs.hold h where h.showtime.id = :showtimeId")
    List<HoldSeat> findAllWithHoldByShowtimeId(@Param("showtimeId") Long showtimeId);

    @Query("select count(hs) from HoldSeat hs where hs.hold.member.id = :memberId and hs.hold.showtime.id = :showtimeId and hs.hold.status = :status")
    long countByMemberIdAndShowtimeIdAndStatus(@Param("memberId") Long memberId,
                                               @Param("showtimeId") Long showtimeId,
                                               @Param("status") HoldStatus status);

    @Query("select hs.seat.id from HoldSeat hs where hs.hold.id = :holdId")
    List<Long> findSeatIdsByHoldId(@Param("holdId") Long holdId);
}
