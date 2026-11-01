package com.ticketflow.domain.member;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.TestClock;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.fixture.AuthHeaders;
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

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberApiTest {

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
    void 가입하면_회원의_id_이메일_이름이_보인다() throws Exception {
        signup("member@ticketflow.test", "password1234", "회원1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("member@ticketflow.test"))
                .andExpect(jsonPath("$.name").value("회원1"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void 가입한_회원이_로그인해서_받은_토큰으로_내_정보를_조회한다() throws Exception {
        signup("member@ticketflow.test", "password1234", "회원1").andExpect(status().isCreated());

        String token = accessToken(login("member@ticketflow.test", "password1234").andExpect(status().isOk()));

        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("member@ticketflow.test"))
                .andExpect(jsonPath("$.name").value("회원1"));
    }

    @Test
    void 이미_가입한_이메일로는_가입할_수_없다() throws Exception {
        signup("member@ticketflow.test", "password1234", "회원1").andExpect(status().isCreated());

        signup("member@ticketflow.test", "password5678", "회원2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 이메일_형식이_아니면_가입할_수_없다() throws Exception {
        signup("not-an-email", "password1234", "회원1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));
    }

    @Test
    void 비밀번호가_8자보다_짧으면_가입할_수_없다() throws Exception {
        signup("member@ticketflow.test", "short12", "회원1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
    }

    @Test
    void 이름이_비어_있으면_가입할_수_없다() throws Exception {
        signup("member@ticketflow.test", "password1234", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEMBER_NAME"));
    }

    @Test
    void 비밀번호가_틀리면_로그인할_수_없다() throws Exception {
        signup("member@ticketflow.test", "password1234", "회원1").andExpect(status().isCreated());

        login("member@ticketflow.test", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 가입하지_않은_이메일은_비밀번호가_틀린_것과_같은_오류가_온다() throws Exception {
        login("nobody@ticketflow.test", "password1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 토큰_없이_내_정보를_조회하면_로그인이_필요하다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void 서명이_틀린_토큰으로_요청하면_토큰이_올바르지_않다는_오류가_온다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        String forged = authHeaders.bearer(fixture.member().getId()) + "x";

        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void 만료된_토큰으로_요청하면_토큰이_만료됐다는_오류가_온다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        String bearer = authHeaders.bearer(fixture.member().getId());
        testClock.advance(Duration.ofHours(1));

        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void 만료_직전의_토큰은_받아들인다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);
        String bearer = authHeaders.bearer(fixture.member().getId());
        testClock.advance(Duration.ofHours(1).minusSeconds(1));

        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @Test
    void 없는_회원의_토큰으로_내_정보를_조회하면_회원이_없다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(Long.MAX_VALUE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 토큰_없이_선점하면_로그인이_필요하다는_오류가_온다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);

        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void 좌석_조회는_로그인하지_않아도_된다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(1);

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", fixture.showtime().getId()))
                .andExpect(status().isOk());
    }

    private ResultActions signup(String email, String password, String name) throws Exception {
        return mockMvc.perform(post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "name": "%s"}
                        """.formatted(email, password, name)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String accessToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
