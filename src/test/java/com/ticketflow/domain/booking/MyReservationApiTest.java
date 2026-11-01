package com.ticketflow.domain.booking;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.TestClock;
import com.ticketflow.fixture.AuthHeaders;
import com.ticketflow.fixture.TestMembers;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyReservationApiTest {

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

    @AfterEach
    void resetClock() {
        testClock.reset();
    }

    @Test
    void 내_예매_목록은_공연_회차_좌석_금액_상태와_함께_조회된다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        mockMvc.perform(get("/api/members/me/reservations")
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(fixture.member().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reservationId))
                .andExpect(jsonPath("$[0].performanceTitle").value("레미제라블"))
                .andExpect(jsonPath("$[0].showtimeId").value(fixture.showtime().getId()))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-01T19:00:00"))
                .andExpect(jsonPath("$[0].seats[0].row").value("A"))
                .andExpect(jsonPath("$[0].seats[0].number").value(1))
                .andExpect(jsonPath("$[0].seats[1].number").value(2))
                .andExpect(jsonPath("$[0].totalPrice").value(100000))
                .andExpect(jsonPath("$[0].status").value("RESERVED"));
    }

    @Test
    void 취소한_예매는_취소됨_상태로_조회된다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));
        cancel(fixture.member(), reservationId);

        mockMvc.perform(get("/api/members/me/reservations")
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(fixture.member().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reservationId))
                .andExpect(jsonPath("$[0].status").value("RESERVATION_CANCELLED"));
    }

    @Test
    void 다른_회원의_예매는_내_예매_목록에_나오지_않는다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(TestMembers.member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        mockMvc.perform(get("/api/members/me/reservations")
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(otherMember.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 결제하지_않은_선점은_내_예매_목록에_나오지_않는다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        hold(fixture.member(), fixture, fixture.seatIds());

        mockMvc.perform(get("/api/members/me/reservations")
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(fixture.member().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void cancel(Member member, Long reservationId) throws Exception {
        mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId())))
                .andExpect(status().isOk());
    }

    private Long pay(Member member, Long holdId) throws Exception {
        String body = mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long hold(Member member, HoldFixture fixture, List<Long> seatIds) throws Exception {
        String body = mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId()))
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
