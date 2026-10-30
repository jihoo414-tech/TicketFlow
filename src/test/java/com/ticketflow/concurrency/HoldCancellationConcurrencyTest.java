package com.ticketflow.concurrency;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.TestClock;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.booking.repository.ReservationRepository;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import com.ticketflow.fixture.HoldFixture;
import com.ticketflow.fixture.HoldFixtureFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
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

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HoldCancellationConcurrencyTest {

    private static final int REQUEST_COUNT = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestClock testClock;

    @Autowired
    HoldFixtureFactory holdFixtureFactory;

    @Autowired
    ReservationRepository reservationRepository;

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

    @Autowired
    MemberRepository memberRepository;

    @AfterEach
    void cleanUp() {
        testClock.reset();
        reservationRepository.deleteAllInBatch();
        holdSeatRepository.deleteAllInBatch();
        holdRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        showtimeRepository.deleteAllInBatch();
        performanceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @RepeatedTest(10)
    void 같은_선점에_결제와_취소가_동시에_오면_한쪽만_성공한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture);

        List<Integer> statuses = runConcurrently(List.of(
                () -> payStatus(fixture, holdId),
                () -> cancelStatus(fixture, holdId)));

        int payStatus = statuses.get(0);
        int cancelStatus = statuses.get(1);
        boolean paid = payStatus == 201;
        assertThat(List.of(payStatus, cancelStatus)).containsExactlyInAnyOrder(paid ? 201 : 409, paid ? 409 : 200);
        assertThat(reservationRepository.count()).isEqualTo(paid ? 1 : 0);
        assertThat(seatStatuses(fixture)).containsOnly(paid ? "RESERVED" : "AVAILABLE");
    }

    @RepeatedTest(10)
    void 같은_선점을_동시에_여러_번_취소하면_한_번만_성공한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture);

        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < REQUEST_COUNT; i++) {
            requests.add(() -> cancelStatus(fixture, holdId));
        }
        List<Integer> statuses = runConcurrently(requests);

        assertThat(statuses).containsOnlyOnce(200).containsOnly(200, 409);
        assertThat(seatStatuses(fixture)).containsOnly("AVAILABLE");
    }

    private List<Integer> runConcurrently(List<Callable<Integer>> requests) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
            for (Callable<Integer> request : requests) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        return statuses;
    }

    private int payStatus(HoldFixture fixture, Long holdId) throws Exception {
        return mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int cancelStatus(HoldFixture fixture, Long holdId) throws Exception {
        return mockMvc.perform(post("/api/holds/{holdId}/cancellation", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private List<String> seatStatuses(HoldFixture fixture) throws Exception {
        String body = mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", fixture.showtime().getId()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$[*].status");
    }

    private Long hold(HoldFixture fixture) throws Exception {
        String body = mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
