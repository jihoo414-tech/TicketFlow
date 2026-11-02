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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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
class ReservationCancellationConcurrencyTest {

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

    @RepeatedTest(10)
    void 예매_취소와_다른_회원의_선점이_동시에_오면_좌석은_한쪽에만_돌아간다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        Member otherMember = memberRepository.save(TestMembers.member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture));

        List<Integer> statuses = runConcurrently(List.of(
                () -> cancelStatus(fixture.member(), reservationId),
                () -> holdStatus(otherMember, fixture)));

        int cancelStatus = statuses.get(0);
        int holdStatus = statuses.get(1);
        boolean otherHeld = holdStatus == 201;
        assertThat(cancelStatus).isEqualTo(200);
        assertThat(holdStatus).isIn(201, 409);
        assertThat(seatStatuses(fixture)).containsOnly(otherHeld ? "HELD" : "AVAILABLE");
    }

    @RepeatedTest(10)
    void 같은_예매를_동시에_여러_번_취소하면_모두_성공한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture));

        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < REQUEST_COUNT; i++) {
            requests.add(() -> cancelStatus(fixture.member(), reservationId));
        }
        List<Integer> statuses = runConcurrently(requests);

        assertThat(statuses).containsOnly(200);
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

    private int cancelStatus(Member member, Long reservationId) throws Exception {
        return mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId())))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int holdStatus(Member member, HoldFixture fixture) throws Exception {
        return holdRequest(member, fixture)
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

    private Long pay(Member member, Long holdId) throws Exception {
        String body = mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long hold(Member member, HoldFixture fixture) throws Exception {
        String body = holdRequest(member, fixture)
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions holdRequest(Member member, HoldFixture fixture) throws Exception {
        return mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"seatIds": %s}
                        """.formatted(fixture.seatIds())));
    }
}
