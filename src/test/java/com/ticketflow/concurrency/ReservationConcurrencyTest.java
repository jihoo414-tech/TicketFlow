package com.ticketflow.concurrency;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.TestClock;
import com.ticketflow.fixture.AuthHeaders;
import com.ticketflow.fixture.TestMembers;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.booking.repository.ReservationRepository;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ZoneRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import com.ticketflow.fixture.HoldFixture;
import com.ticketflow.fixture.HoldFixtureFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
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
class ReservationConcurrencyTest {

    private static final int REQUEST_COUNT = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthHeaders authHeaders;

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
    ZoneRepository zoneRepository;

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
        zoneRepository.deleteAllInBatch();
        showtimeRepository.deleteAllInBatch();
        performanceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 같은_선점을_동시에_여러_번_결제해도_예매는_하나만_생긴다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture);

        List<MockHttpServletResponse> responses = payConcurrently(fixture, holdId);

        assertThat(responses).extracting(MockHttpServletResponse::getStatus)
                .containsOnlyOnce(201)
                .containsOnly(201, 409);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @RepeatedTest(10)
    void 만료_시각에_결제와_다른_회원의_선점이_겹쳐도_좌석은_한쪽에만_돌아간다(RepetitionInfo repetition) throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture);
        testClock.advance(Duration.ofMinutes(5).minusSeconds(1));

        List<Callable<Integer>> requests = new ArrayList<>();
        requests.add(() -> payStatus(fixture, holdId));
        for (int i = 0; i < REQUEST_COUNT; i++) {
            Member other = memberRepository.save(TestMembers.member("회원" + (i + 2)));
            requests.add(() -> holdStatus(fixture, other));
        }

        Duration expiringDelay = Duration.ofMillis(repetition.getCurrentRepetition() * 20L);
        List<Integer> statuses = runWhileExpiring(requests, expiringDelay);

        boolean paid = statuses.get(0) == 201;
        long heldByOthers = statuses.subList(1, statuses.size()).stream()
                .filter(status -> status == 201)
                .count();
        assertThat(heldByOthers).isLessThanOrEqualTo(paid ? 0 : 1);
        assertThat(seatStatuses(fixture)).containsOnly(paid ? "RESERVED" : heldByOthers == 1 ? "HELD" : "AVAILABLE");
    }

    private List<Integer> runWhileExpiring(List<Callable<Integer>> requests, Duration expiringDelay) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size() + 1)) {
            for (Callable<Integer> request : requests) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            executor.submit(() -> {
                start.await();
                Thread.sleep(expiringDelay);
                testClock.advance(Duration.ofSeconds(1));
                return null;
            });
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
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(fixture.member().getId())))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int holdStatus(HoldFixture fixture, Member member) throws Exception {
        return mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private List<String> seatStatuses(HoldFixture fixture) throws Exception {
        String body = mockMvc.perform(get("/api/zones/{zoneId}/seats", fixture.zone().getId()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$[*].status");
    }

    private List<MockHttpServletResponse> payConcurrently(HoldFixture fixture, Long holdId) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                                    .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(fixture.member().getId())))
                            .andReturn()
                            .getResponse();
                }));
            }
            start.countDown();
        }
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (Future<MockHttpServletResponse> future : futures) {
            responses.add(future.get());
        }
        return responses;
    }

    private Long hold(HoldFixture fixture) throws Exception {
        String body = mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(fixture.member().getId()))
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
