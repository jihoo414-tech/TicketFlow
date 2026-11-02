package com.ticketflow.fixture;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.entity.Zone;

import java.util.List;

public record HoldFixture(Showtime showtime, Zone zone, List<Seat> seats, Member member) {

    public List<Long> seatIds() {
        return seats.stream()
                .map(Seat::getId)
                .toList();
    }
}
