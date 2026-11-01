package com.ticketflow.domain.booking.repository;

import com.ticketflow.domain.booking.dto.OccupiedSeat;
import com.ticketflow.domain.booking.entity.HoldSeat;
import com.ticketflow.domain.booking.entity.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface HoldSeatRepository extends JpaRepository<HoldSeat, Long> {

    @Query("select hs from HoldSeat hs join fetch hs.hold where hs.seat.id in :seatIds")
    List<HoldSeat> findAllWithHoldBySeatIdIn(@Param("seatIds") List<Long> seatIds);

    @Query("""
            select new com.ticketflow.domain.booking.dto.OccupiedSeat(hs.seat.id, h.status)
            from HoldSeat hs join hs.hold h
            where h.showtime.id = :showtimeId
              and (h.status = :reserved or (h.status = :held and h.expiresAt > :now))
            """)
    List<OccupiedSeat> findOccupyingByShowtimeId(@Param("showtimeId") Long showtimeId,
                                                 @Param("reserved") HoldStatus reserved,
                                                 @Param("held") HoldStatus held,
                                                 @Param("now") LocalDateTime now);

    @Query("select count(hs) from HoldSeat hs where hs.hold.member.id = :memberId and hs.hold.showtime.id = :showtimeId and hs.hold.status = :status")
    long countByMemberIdAndShowtimeIdAndStatus(@Param("memberId") Long memberId,
                                               @Param("showtimeId") Long showtimeId,
                                               @Param("status") HoldStatus status);

    @Query("select hs.seat.id from HoldSeat hs where hs.hold.id = :holdId")
    List<Long> findSeatIdsByHoldId(@Param("holdId") Long holdId);

    @Query("""
            select hs from HoldSeat hs join fetch hs.seat s
            where hs.hold.id in :holdIds
            order by s.seatRow, s.seatNo
            """)
    List<HoldSeat> findAllWithSeatByHoldIdIn(@Param("holdIds") List<Long> holdIds);
}
