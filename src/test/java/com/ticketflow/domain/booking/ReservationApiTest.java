package com.ticketflow.domain.booking;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationApiTest {

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
    void 만료_전_자기_선점을_결제하면_예매가_생긴다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());

        mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.holdId").value(holdId))
                .andExpect(jsonPath("$.memberId").value(fixture.member().getId()));
    }

    @Test
    void 예매된_좌석은_선점_만료_시각이_지나도_다른_회원이_선점할_수_없다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());
        pay(fixture.member(), holdId);

        testClock.advance(Duration.ofMinutes(5));

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", otherMember.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_HELD"));
    }

    @Test
    void 회차의_좌석을_조회할때_예매된_좌석이_반영된다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(3);
        List<Long> seatIds = fixture.seatIds();
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, List.of(seatIds.get(0), seatIds.get(1)));
        pay(fixture.member(), holdId);

        testClock.advance(Duration.ofMinutes(5));

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", fixture.showtime().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].number").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$[*].status").value(contains("RESERVED", "RESERVED", "AVAILABLE")));
    }

    @Test
    void 선점이_만료된_뒤에_결제하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());

        testClock.advance(Duration.ofMinutes(5));

        mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    }

    @Test
    void 다른_회원의_선점을_결제하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());

        mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header("X-Member-Id", otherMember.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_OWNED"));
    }

    @Test
    void 결제된_선점을_만료_시각_이후에_다시_결제하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());
        pay(fixture.member(), holdId);

        testClock.advance(Duration.ofMinutes(6));

        mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_ALREADY_RESERVED"));
    }

    @Test
    void 예매가_있는_회차에서_합쳐서_4석까지는_다시_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        List<Long> seatIds = fixture.seatIds();
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, List.of(seatIds.get(0), seatIds.get(1)));
        pay(fixture.member(), holdId);

        hold(fixture.member(), fixture, List.of(seatIds.get(2), seatIds.get(3)));
    }

    @Test
    void 예매와_합쳐서_4석을_넘게_선점하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(5);
        List<Long> seatIds = fixture.seatIds();
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, List.of(seatIds.get(0), seatIds.get(1)));
        pay(fixture.member(), holdId);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", fixture.member().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(List.of(seatIds.get(2), seatIds.get(3), seatIds.get(4)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));
    }

    @Test
    void 존재하지_않는_선점을_결제하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);

        mockMvc.perform(post("/api/holds/{holdId}/payment", Long.MAX_VALUE)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
    }

    private Long pay(Member member, Long holdId) throws Exception {
        String body = mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header("X-Member-Id", member.getId()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long hold(Member member, HoldFixture fixture, List<Long> seatIds) throws Exception {
        String body = mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header("X-Member-Id", member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(seatIds)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
