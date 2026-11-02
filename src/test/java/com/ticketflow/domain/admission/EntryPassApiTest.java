package com.ticketflow.domain.admission;

import com.ticketflow.FakeQueueClient;
import com.ticketflow.TestClock;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.fixture.AuthHeaders;
import com.ticketflow.fixture.EntryPassTokens;
import com.ticketflow.fixture.HoldFixture;
import com.ticketflow.fixture.HoldFixtureFactory;
import com.ticketflow.fixture.TestMembers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntryPassApiTest {

    private static final String ENTRY_TOKEN = "X-Entry-Token";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthHeaders authHeaders;

    @Autowired
    EntryPassTokens entryPassTokens;

    @Autowired
    HoldFixtureFactory holdFixtureFactory;

    @Autowired
    FakeQueueClient queueClient;

    @Autowired
    TestClock testClock;

    @Autowired
    MemberRepository memberRepository;

    HoldFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = holdFixtureFactory.createHoldFixture(4);
        testClock.setTo(BOOKING_OPEN_AT);
        queueClient.reset();
    }

    @AfterEach
    void tearDown() {
        testClock.reset();
    }

    @Test
    void 로그인하지_않으면_구역_목록을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/showtimes/{showtimeId}/zones", fixture.showtime().getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void 입장권_없이_구역_목록을_조회하면_입장권이_필요하다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/showtimes/{showtimeId}/zones", fixture.showtime().getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(memberId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENTRY_PASS_REQUIRED"));
    }

    @Test
    void 서명이_틀린_입장권으로는_구역_좌석을_볼_수_없다() throws Exception {
        String forged = entryPassTokens.forge(memberId(), showtimeId());

        zoneSeats(forged)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENTRY_PASS_INVALID"));
    }

    @Test
    void 다른_회차의_입장권으로는_구역_좌석을_볼_수_없다() throws Exception {
        String otherShowtimePass = entryPassTokens.issue(memberId(), showtimeId() + 1);

        zoneSeats(otherShowtimePass)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENTRY_PASS_INVALID"));
    }

    @Test
    void 다른_회원의_입장권으로는_선점할_수_없다() throws Exception {
        String otherMemberPass = entryPassTokens.issue(memberId() + 1, showtimeId());

        hold(otherMemberPass)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENTRY_PASS_INVALID"));
    }

    @Test
    void 만료된_입장권으로는_선점할_수_없다() throws Exception {
        String pass = entryPassTokens.issue(memberId(), showtimeId());

        testClock.advance(Duration.ofMinutes(5));

        hold(pass)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENTRY_PASS_EXPIRED"));
    }

    @Test
    void 선점에_성공하면_그_입장권으로는_다시_구역_좌석을_볼_수_없다() throws Exception {
        String pass = entryPassTokens.issue(memberId(), showtimeId());
        hold(pass).andExpect(status().isCreated());

        zoneSeats(pass)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENTRY_PASS_USED"));
    }

    @Test
    void 선점에_성공하면_대기열_서버에_자리_비움을_알린다() throws Exception {
        String pass = entryPassTokens.issue(memberId(), showtimeId());

        hold(pass).andExpect(status().isCreated());

        assertThat(queueClient.releasedPassIds()).containsExactly(EntryPassTokens.idOf(pass));
    }

    @Test
    void 선점에_실패하면_입장권을_계속_쓸_수_있고_자리_비움을_알리지_않는다() throws Exception {
        Long otherMemberId = memberRepository.save(TestMembers.member("회원2")).getId();
        String otherPass = entryPassTokens.issue(otherMemberId, showtimeId());
        hold(otherMemberId, otherPass).andExpect(status().isCreated());
        String pass = entryPassTokens.issue(memberId(), showtimeId());

        hold(pass).andExpect(status().isConflict());

        zoneSeats(pass).andExpect(status().isOk());
        assertThat(queueClient.releasedPassIds()).containsExactly(EntryPassTokens.idOf(otherPass));
    }

    @Test
    void 대기열_서버에_알리지_못해도_선점은_성공한다() throws Exception {
        queueClient.failNextReleases();

        hold(entryPassTokens.issue(memberId(), showtimeId()))
                .andExpect(status().isCreated());
    }

    private ResultActions hold(String entryPass) throws Exception {
        return hold(memberId(), entryPass);
    }

    private ResultActions hold(Long memberId, String entryPass) throws Exception {
        return mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", showtimeId())
                .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(memberId))
                .header(ENTRY_TOKEN, entryPass)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"seatIds": %s}
                        """.formatted(fixture.seatIds().subList(0, 2))));
    }

    private ResultActions zoneSeats(String entryPass) throws Exception {
        return mockMvc.perform(get("/api/zones/{zoneId}/seats", fixture.zone().getId())
                .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(memberId()))
                .header(ENTRY_TOKEN, entryPass));
    }

    private Long showtimeId() {
        return fixture.showtime().getId();
    }

    private Long memberId() {
        return fixture.member().getId();
    }
}
