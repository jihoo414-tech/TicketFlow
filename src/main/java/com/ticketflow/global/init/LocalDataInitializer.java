package com.ticketflow.global.init;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// 로컬 실행에서만 초기 데이터를 넣는다. 테스트는 필요한 데이터를 스스로 만든다.
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private static final List<String> SEAT_ROWS = List.of("A", "B", "C", "D", "E");
    private static final int SEATS_PER_ROW = 10;

    private final Clock clock;
    private final MemberRepository memberRepository;
    private final PerformanceRepository performanceRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;

    @Override
    public void run(ApplicationArguments args) {
        memberRepository.saveAll(List.of(
                new Member("회원1"),
                new Member("회원2"),
                new Member("회원3")
        ));


        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);

        Performance lesMis = performanceRepository.save(new Performance("레미제라블"));
        Performance phantom = performanceRepository.save(new Performance("오페라의 유령"));

        List<Showtime> showtimes = showtimeRepository.saveAll(List.of(
                new Showtime(lesMis, now.plusDays(7), now.minusMinutes(10), 150000),
                new Showtime(lesMis, now.plusDays(8), now.plusMinutes(3), 150000),
                new Showtime(phantom, now.plusDays(14), now.minusMinutes(10), 130000)
        ));

        for (Showtime showtime : showtimes) {
            seatRepository.saveAll(seatsOf(showtime));
        }
    }

    private List<Seat> seatsOf(Showtime showtime) {
        List<Seat> seats = new ArrayList<>();
        for (String row : SEAT_ROWS) {
            for (int no = 1; no <= SEATS_PER_ROW; no++) {
                seats.add(new Seat(showtime, row, no));
            }
        }
        return seats;
    }
}
