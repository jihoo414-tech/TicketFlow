package com.ticketflow.domain.booking;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.TestClock;
import com.ticketflow.fixture.AuthHeaders;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.member.entity.Member;
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
import org.springframework.test.web.servlet.ResultActions;
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
class RemainingSeatCountApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthHeaders authHeaders;

    @Autowired
    TestClock testClock;

    @Autowired
    HoldFixtureFactory holdFixtureFactory;

    @AfterEach
    void resetClock() {
        testClock.reset();
    }

    @Test
    void 예매가_없는_회원의_남은_매수는_4석이다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);

        getRemainingSeatCount(fixture.member(), fixture.showtime().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeatCount").value(4));
    }

    @Test
    void 예매한_좌석이_2석이면_남은_매수는_2석이다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        testClock.setTo(BOOKING_OPEN_AT);
        pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds().subList(0, 2)));

        getRemainingSeatCount(fixture.member(), fixture.showtime().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeatCount").value(2));
    }

    @Test
    void 예매를_취소하면_남은_매수가_돌아온다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds().subList(0, 2)));
        cancelReservation(fixture.member(), reservationId);

        getRemainingSeatCount(fixture.member(), fixture.showtime().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeatCount").value(4));
    }

    @Test
    void 결제하지_않은_선점은_남은_매수에서_빼지_않는다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        testClock.setTo(BOOKING_OPEN_AT);
        hold(fixture.member(), fixture, fixture.seatIds().subList(0, 2));

        getRemainingSeatCount(fixture.member(), fixture.showtime().getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeatCount").value(4));
    }

    @Test
    void 없는_회차의_남은_매수는_조회할_수_없다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);

        getRemainingSeatCount(fixture.member(), Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOWTIME_NOT_FOUND"));
    }

    private ResultActions getRemainingSeatCount(Member member, Long showtimeId) throws Exception {
        return mockMvc.perform(get("/api/members/me/showtimes/{showtimeId}/remaining-seat-count", showtimeId)
                .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId())));
    }

    private void cancelReservation(Member member, Long reservationId) throws Exception {
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
