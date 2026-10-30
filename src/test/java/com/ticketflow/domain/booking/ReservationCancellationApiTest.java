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

import java.util.List;

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationCancellationApiTest {

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
    void 회차_시작_전에_자기_예매를_취소하면_취소된_상태를_응답받는다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", reservationId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.status").value("RESERVATION_CANCELLED"));
    }

    @Test
    void 다른_회원의_예매를_취소하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", reservationId)
                        .header("X-Member-Id", otherMember.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_OWNED"));
    }

    @Test
    void 회차_시작_시각_정각에는_예매를_취소할_수_없다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        testClock.setTo(fixture.showtime().getStartsAt());

        mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", reservationId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOWTIME_ALREADY_STARTED"));
    }

    @Test
    void 이미_취소된_예매를_회차_시작_후에_다시_취소해도_성공한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));
        cancel(fixture.member(), reservationId);

        testClock.setTo(fixture.showtime().getStartsAt());

        mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", reservationId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.status").value("RESERVATION_CANCELLED"));
    }

    @Test
    void 취소된_예매의_좌석은_다른_회원이_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        cancel(fixture.member(), reservationId);

        hold(otherMember, fixture, fixture.seatIds());
    }

    @Test
    void 예매를_취소하면_그_좌석들은_4석_제한_계산에서_빠진다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(4);
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        cancel(fixture.member(), reservationId);

        hold(fixture.member(), fixture, fixture.seatIds());
    }

    @Test
    void 여러_좌석의_예매를_취소하면_모든_좌석이_함께_열린다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long reservationId = pay(fixture.member(), hold(fixture.member(), fixture, fixture.seatIds()));

        cancel(fixture.member(), reservationId);

        hold(otherMember, fixture, fixture.seatIds());
    }

    @Test
    void 없는_예매를_취소하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);

        mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", Long.MAX_VALUE)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    private void cancel(Member member, Long reservationId) throws Exception {
        mockMvc.perform(post("/api/reservations/{reservationId}/cancellation", reservationId)
                        .header("X-Member-Id", member.getId()))
                .andExpect(status().isOk());
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
