package com.ticketflow.concurrency;

import com.ticketflow.TestClock;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import com.ticketflow.fixture.HoldFixture;
import com.ticketflow.fixture.HoldFixtureFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
    ShowtimeRepository showtimeRepository;

    @Autowired
    PerformanceRepository performanceRepository;

    @AfterEach
    void cleanUp() {
        testClock.reset();
        holdSeatRepository.deleteAllInBatch();
        holdRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        showtimeRepository.deleteAllInBatch();
        performanceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 여러_회원이_동시에_한_좌석을_선점하면_정확히_한_명만_선점한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);

        List<Member> members = memberRepository.saveAll(IntStream.rangeClosed(1, MEMBER_COUNT)
                .mapToObj(memberNo -> new Member("동시회원" + memberNo))
                .toList());


        testClock.setTo(BOOKING_OPEN_AT);

        List<Integer> statuses = holdConcurrently(fixture, members);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(MEMBER_COUNT - 1);
    }

    private List<Integer> holdConcurrently(HoldFixture fixture, List<Member> members) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(members.size())) {
            for (Member member : members) {
                futures.add(executor.submit(holdRequest(fixture, member, start)));
            }
            start.countDown();
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        return statuses;
    }

    private Callable<Integer> holdRequest(HoldFixture fixture, Member member, CountDownLatch start) {
        return () -> {
            start.await();
            return mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                            .header("X-Member-Id", member.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"seatIds": %s}
                                    """.formatted(fixture.seatIds())))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }
}
