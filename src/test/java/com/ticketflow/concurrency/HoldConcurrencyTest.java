package com.ticketflow.concurrency;

import com.ticketflow.TestClock;
import com.ticketflow.fixture.AuthHeaders;
import com.ticketflow.fixture.TestMembers;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ZoneRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import com.ticketflow.fixture.HoldFixture;
import com.ticketflow.fixture.HoldFixtureFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HoldConcurrencyTest {

    private static final int MEMBER_COUNT = 10;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthHeaders authHeaders;

    @Autowired
    TestClock testClock;

    @Autowired
    HoldFixtureFactory holdFixtureFactory;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    HoldSeatRepository holdSeatRepository;

    @Autowired
    HoldRepository holdRepository;

    @Autowired
    SeatRepository seatRepository;

    @Autowired
    ZoneRepository zoneRepository;

    @Autowired
    ShowtimeRepository showtimeRepository;

    @Autowired
    PerformanceRepository performanceRepository;

    @AfterEach
    void cleanUp() {
        testClock.reset();
        holdSeatRepository.deleteAllInBatch();
        holdRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        zoneRepository.deleteAllInBatch();
        showtimeRepository.deleteAllInBatch();
        performanceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 여러_회원이_동시에_한_좌석을_선점하면_정확히_한_명만_선점한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);

        List<Member> members = memberRepository.saveAll(IntStream.rangeClosed(1, MEMBER_COUNT)
                .mapToObj(memberNo -> TestMembers.member("동시회원" + memberNo))
                .toList());


        testClock.setTo(BOOKING_OPEN_AT);

        List<Integer> statuses = holdConcurrently(fixture, members.stream()
                .map(member -> new HoldAttempt(member, fixture.seatIds()))
                .toList());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(MEMBER_COUNT - 1);
    }

    @Test
    void 여러_회원이_일부만_겹치는_좌석_묶음을_동시에_선점하면_한_명만_선점한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(3);
        List<Long> seatIds = fixture.seatIds();
        Member memberA = memberRepository.save(TestMembers.member("회원A"));
        Member memberB = memberRepository.save(TestMembers.member("회원B"));
        Member memberC = memberRepository.save(TestMembers.member("회원C"));
        testClock.setTo(BOOKING_OPEN_AT);

        List<Integer> statuses = holdConcurrently(fixture, List.of(
                new HoldAttempt(memberA, List.of(seatIds.get(0), seatIds.get(1))),
                new HoldAttempt(memberB, List.of(seatIds.get(1), seatIds.get(2)))));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        Long loserOnlySeatId = statuses.get(0) == 409 ? seatIds.get(0) : seatIds.get(2);
        int statusOfLoserOnlySeat = holdRequest(fixture, new HoldAttempt(memberC, List.of(loserOnlySeatId)), new CountDownLatch(0)).call();
        assertThat(statusOfLoserOnlySeat).isEqualTo(201);
    }

    @Test
    void 같은_회원이_같은_회차에_동시에_여러_번_선점하면_한_번만_선점한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        testClock.setTo(BOOKING_OPEN_AT);

        List<Integer> statuses = holdConcurrently(fixture, fixture.seatIds().stream()
                .map(seatId -> new HoldAttempt(fixture.member(), List.of(seatId)))
                .toList());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(3);
    }

    private List<Integer> holdConcurrently(HoldFixture fixture, List<HoldAttempt> attempts) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts.size())) {
            for (HoldAttempt attempt : attempts) {
                futures.add(executor.submit(holdRequest(fixture, attempt, start)));
            }
            start.countDown();
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        return statuses;
    }

    private Callable<Integer> holdRequest(HoldFixture fixture, HoldAttempt attempt, CountDownLatch start) {
        return () -> {
            start.await();
            return mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                            .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(attempt.member().getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"seatIds": %s}
                                    """.formatted(attempt.seatIds())))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    private record HoldAttempt(Member member, List<Long> seatIds) {
    }
}
