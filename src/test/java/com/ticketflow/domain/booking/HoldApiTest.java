package com.ticketflow.domain.booking;

import com.ticketflow.TestClock;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HoldApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestClock testClock;

    @Autowired
    HoldFixtureFactory holdFixtureFactory;

    @Autowired
    MemberRepository memberRepository;

    @AfterEach
    void resetClock() {
        testClock.reset();
    }

    @Test
    void 회원은_최대_4석까지_좌석을_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.seats[*].id").value(containsInAnyOrder(
                        fixture.seatIds().stream().map(Long::intValue).toArray())))
                .andExpect(jsonPath("$.seats[*].row").value(containsInAnyOrder("A", "A", "A", "A")))
                .andExpect(jsonPath("$.seats[*].number").value(containsInAnyOrder(1, 2, 3, 4)))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-01T10:05:00"));
    }

    @Test
    void 선점하려는_묶음에_다른_선점의_좌석이_있으면_선점이_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        Member otherMember = memberRepository.save(new Member("회원2"));
        List<Long> seatIds = fixture.seatIds();
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(seatIds.get(0), seatIds.get(1)))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", otherMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(seatIds.get(1), seatIds.get(2)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_HELD"));
    }

    @Test
    void 이미_선점이_있는_회차에서_다시_선점하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(3);
        List<Long> seatIds = fixture.seatIds();
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(seatIds.get(0), seatIds.get(1)))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(seatIds.get(2)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_ALREADY_EXISTS"));
    }

    @Test
    void 다른_회차에서는_따로_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        HoldFixture otherShowtimeFixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", otherShowtimeFixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(otherShowtimeFixture.seatIds())))
                .andExpect(status().isCreated());
    }

    @Test
    void 같은_좌석을_중복해서_요청하면_선점이_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Long seatId = fixture.seatIds().get(0);
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(seatId, seatId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SEAT"));
    }

    @Test
    void 존재하지_않는_좌석이_포함되면_선점이_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Long missingSeatId = Long.MAX_VALUE;
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(fixture.seatIds().get(0), missingSeatId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_FOUND"));
    }

    @Test
    void 다른_회차의_좌석이_섞여_있으면_선점이_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        HoldFixture otherShowtimeFixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(fixture.seatIds().get(0), otherShowtimeFixture.seatIds().get(0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWTIME"));
    }

    @Test
    void 선점이_만료된_회차에서는_다시_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(8);
        List<Long> seatIds = fixture.seatIds();
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(seatIds.subList(0, 4))))
                .andExpect(status().isCreated());

        testClock.advance(Duration.ofMinutes(5));

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(seatIds.subList(4, 8))))
                .andExpect(status().isCreated());
    }

    @Test
    void 선점_만료된_좌석은_다른_회원이_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isCreated());

        testClock.advance(Duration.ofMinutes(5));

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", otherMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isCreated());
    }

    @Test
    void 회차의_좌석을_조회할때_선점된_좌석들이_반영된다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(3);
        List<Long> seatIds = fixture.seatIds();
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(seatIds.get(0), seatIds.get(1)))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", fixture.showtime().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].number").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$[*].status").value(contains("HELD", "HELD", "AVAILABLE")));
    }

    @Test
    void 선점_만료_시각이_지나면_회차의_좌석을_조회할때_빈_좌석으로_보인다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isCreated());

        testClock.advance(Duration.ofMinutes(5));

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", fixture.showtime().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status").value(contains("AVAILABLE", "AVAILABLE")));
    }

    @Test
    void 좌석_목록_없이_선점을_요청하면_잘못된_요청으로_거절된다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
