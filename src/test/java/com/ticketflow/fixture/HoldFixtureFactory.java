package com.ticketflow.fixture;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class HoldFixtureFactory {

    public static final LocalDateTime BOOKING_OPEN_AT = LocalDateTime.of(2026, 10, 1, 10, 0, 0);

    private final PerformanceRepository performanceRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final MemberRepository memberRepository;

    public HoldFixture createHoldFixture(int seatCount) {
        Performance performance = performanceRepository.save(new Performance("레미제라블"));
        Showtime showtime = showtimeRepository.save(Showtime.create(
                performance,
                LocalDateTime.of(2026, 11, 1, 19, 0, 0),
                BOOKING_OPEN_AT,
                50000
        ));
        List<Seat> seats = seatRepository.saveAll(IntStream.rangeClosed(1, seatCount)
                .mapToObj(seatNo -> new Seat(showtime, "A", seatNo))
                .toList());
        Member member = memberRepository.save(new Member("회원1"));
        return new HoldFixture(showtime, seats, member);
    }
}
