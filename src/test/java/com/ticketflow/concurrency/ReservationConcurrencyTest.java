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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReservationConcurrencyTest {

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

    @Test
    void 같은_선점을_동시에_여러_번_결제해도_예매는_하나만_생긴다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture);

        List<MockHttpServletResponse> responses = payConcurrently(fixture, holdId);

        assertThat(responses).allMatch(response -> response.getStatus() == 201);
        assertThat(responses).extracting(this::reservationIdOf).containsOnly(reservationIdOf(responses.get(0)));
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    private List<MockHttpServletResponse> payConcurrently(HoldFixture fixture, Long holdId) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT)) {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                                    .header("X-Member-Id", fixture.member().getId()))
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

    private Long reservationIdOf(MockHttpServletResponse response) {
        try {
            return ((Number) JsonPath.read(response.getContentAsString(), "$.id")).longValue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
