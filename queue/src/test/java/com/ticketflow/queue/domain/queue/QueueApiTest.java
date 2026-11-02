package com.ticketflow.queue.domain.queue;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.queue.EntryPassTokens;
import com.ticketflow.queue.FakeShowtimeClient;
import com.ticketflow.queue.MemberTokens;
import com.ticketflow.queue.QueueTestConfiguration;
import com.ticketflow.queue.TestClock;
import com.ticketflow.queue.domain.queue.service.WaitingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(QueueTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class QueueApiTest {

    private static final long SHOWTIME_ID = 10L;
    private static final long OTHER_SHOWTIME_ID = 20L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FakeShowtimeClient showtimeClient;

    @Autowired
    WaitingQueue waitingQueue;

    @Autowired
    TestClock clock;

    @Test
    void 로그인하지_않으면_줄을_설_수_없다() throws Exception {
        mockMvc.perform(post("/api/showtimes/{showtimeId}/queue", SHOWTIME_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void 없는_회차에는_줄을_설_수_없다() throws Exception {
        join(1L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOWTIME_NOT_FOUND"));
    }

    @Test
    void 예매_오픈_전에는_줄을_설_수_없다() throws Exception {
        showtimeClient.add(SHOWTIME_ID, LocalDateTime.of(2026, 10, 1, 10, 0, 0));

        join(1L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_OPEN"));
    }

    @Test
    void 줄을_서면_순번과_다시_확인할_때까지의_초를_받는다() throws Exception {
        openShowtime(SHOWTIME_ID);
        join(1L);

        join(2L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.retryAfterSeconds").value(1));
    }

    @Test
    void 다른_회차를_기다리는_중에는_줄을_설_수_없다() throws Exception {
        openShowtime(SHOWTIME_ID);
        openShowtime(OTHER_SHOWTIME_ID);
        join(1L);

        join(1L, OTHER_SHOWTIME_ID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_QUEUE"));
    }

    @Test
    void 차례가_되면_순번_확인_응답으로_입장권을_받는다() throws Exception {
        openShowtime(SHOWTIME_ID);
        join(1L);
        waitingQueue.admit();

        String body = checkQueue(1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-01T00:05:00Z"))
                .andReturn().getResponse().getContentAsString();

        Jwt entryPass = EntryPassTokens.decode(JsonPath.read(body, "$.entryPass"));
        assertThat(entryPass.getSubject()).isEqualTo("1");
        assertThat(entryPass.getClaimAsString("showtimeId")).isEqualTo("10");
        assertThat(entryPass.getId()).isNotBlank();
        assertThat(entryPass.getExpiresAt()).isEqualTo(Instant.parse("2026-10-01T00:05:00Z"));
    }

    @Test
    void 입장한_뒤_다시_확인해도_같은_입장권을_받는다() throws Exception {
        openShowtime(SHOWTIME_ID);
        join(1L);
        waitingQueue.admit();
        String first = entryPassOf(1L);

        clock.advance(Duration.ofSeconds(30));

        assertThat(entryPassOf(1L)).isEqualTo(first);
    }

    @Test
    void 대기_취소하면_줄에_없다는_응답이_온다() throws Exception {
        openShowtime(SHOWTIME_ID);
        join(1L);

        mockMvc.perform(delete("/api/showtimes/{showtimeId}/queue/me", SHOWTIME_ID)
                        .header(HttpHeaders.AUTHORIZATION, MemberTokens.bearer(1L)))
                .andExpect(status().isNoContent());

        checkQueue(1L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_IN_QUEUE"));
    }

    @Test
    void 줄을_서지_않은_회차의_순번을_확인하면_줄에_없다는_오류가_온다() throws Exception {
        openShowtime(SHOWTIME_ID);
        join(1L);

        mockMvc.perform(get("/api/showtimes/{showtimeId}/queue/me", OTHER_SHOWTIME_ID)
                        .header(HttpHeaders.AUTHORIZATION, MemberTokens.bearer(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_IN_QUEUE"));
    }

    @Test
    void 비밀값_없이_자리_비움을_알리면_거절된다() throws Exception {
        mockMvc.perform(post("/internal/entry-passes/{passId}/release", "any-pass"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_SECRET_INVALID"));

        mockMvc.perform(post("/internal/entry-passes/{passId}/release", "any-pass")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_SECRET_INVALID"));
    }

    @Test
    void 자리_비움을_알리면_다음_입장_처리에서_다음_회원이_들어온다() throws Exception {
        openShowtime(SHOWTIME_ID);
        join(1L);
        join(2L);
        join(3L);
        waitingQueue.admit();
        waitingQueue.admit();
        String passId = EntryPassTokens.decode(entryPassOf(1L)).getId();

        mockMvc.perform(post("/internal/entry-passes/{passId}/release", passId)
                        .header("X-Internal-Secret", "test-only-internal-secret"))
                .andExpect(status().isNoContent());
        waitingQueue.admit();

        checkQueue(3L).andExpect(jsonPath("$.status").value("ADMITTED"));
    }

    @Test
    void 줄을_서지_않은_회차의_대기를_취소하면_줄에_없다는_오류가_오고_원래_자리는_남는다() throws Exception {
        openShowtime(SHOWTIME_ID);
        join(1L);

        mockMvc.perform(delete("/api/showtimes/{showtimeId}/queue/me", OTHER_SHOWTIME_ID)
                        .header(HttpHeaders.AUTHORIZATION, MemberTokens.bearer(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_IN_QUEUE"));

        checkQueue(1L).andExpect(jsonPath("$.status").value("WAITING"));
    }

    private String entryPassOf(Long memberId) throws Exception {
        String body = checkQueue(memberId).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.entryPass");
    }

    private void openShowtime(Long showtimeId) {
        showtimeClient.add(showtimeId, LocalDateTime.of(2026, 10, 1, 8, 0, 0));
    }

    private ResultActions checkQueue(Long memberId) throws Exception {
        return mockMvc.perform(get("/api/showtimes/{showtimeId}/queue/me", SHOWTIME_ID)
                .header(HttpHeaders.AUTHORIZATION, MemberTokens.bearer(memberId)));
    }

    private ResultActions join(Long memberId) throws Exception {
        return join(memberId, SHOWTIME_ID);
    }

    private ResultActions join(Long memberId, Long showtimeId) throws Exception {
        return mockMvc.perform(post("/api/showtimes/{showtimeId}/queue", showtimeId)
                .header(HttpHeaders.AUTHORIZATION, MemberTokens.bearer(memberId)));
    }
}
