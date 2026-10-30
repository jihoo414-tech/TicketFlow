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
class HoldCancellationApiTest {

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
    void 자기_선점을_취소하면_취소된_상태를_응답받는다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());

        mockMvc.perform(post("/api/holds/{holdId}/cancellation", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(holdId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void 다른_회원의_선점을_취소하면_실패한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());

        mockMvc.perform(post("/api/holds/{holdId}/cancellation", holdId)
                        .header("X-Member-Id", otherMember.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_OWNED"));
    }

    @Test
    void 취소된_선점의_좌석은_다른_회원이_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        Member otherMember = memberRepository.save(new Member("회원2"));
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());

        mockMvc.perform(post("/api/holds/{holdId}/cancellation", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isOk());

        hold(otherMember, fixture, fixture.seatIds());
    }

    @Test
    void 선점을_취소한_회원은_같은_회차에서_다시_선점할_수_있다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        testClock.setTo(BOOKING_OPEN_AT);
        Long holdId = hold(fixture.member(), fixture, fixture.seatIds());

        mockMvc.perform(post("/api/holds/{holdId}/cancellation", holdId)
                        .header("X-Member-Id", fixture.member().getId()))
                .andExpect(status().isOk());

        hold(fixture.member(), fixture, fixture.seatIds());
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
