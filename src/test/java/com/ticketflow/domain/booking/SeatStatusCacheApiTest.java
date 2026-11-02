package com.ticketflow.domain.booking;

import com.ticketflow.TestClock;
import com.ticketflow.fixture.AuthHeaders;
import com.ticketflow.fixture.EntryPassTokens;
import com.ticketflow.TestTicker;
import com.ticketflow.TestcontainersConfiguration;
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

import java.time.Duration;

import static com.ticketflow.domain.admission.service.EntryPasses.ENTRY_TOKEN;
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
class SeatStatusCacheApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntryPassTokens entryPassTokens;

    @Autowired
    AuthHeaders authHeaders;

    @Autowired
    TestClock testClock;

    @Autowired
    TestTicker testTicker;

    @Autowired
    HoldFixtureFactory holdFixtureFactory;

    @AfterEach
    void resetClock() {
        testClock.reset();
    }

    @Test
    void 같은_회차를_1초_안에_다시_조회하면_그_사이의_선점이_보이지_않는다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        lookUpSeats(fixture);

        hold(fixture);
        testTicker.advance(Duration.ofMillis(999));

        mockMvc.perform(get("/api/zones/{zoneId}/seats", fixture.zone().getId()).with(entryPassTokens.viewer(fixture.showtime().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status").value(contains("AVAILABLE", "AVAILABLE")));
    }

    @Test
    void 조회하고_1초가_지나면_그_사이의_선점이_보인다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        testClock.setTo(BOOKING_OPEN_AT);
        lookUpSeats(fixture);

        hold(fixture);
        testTicker.advance(Duration.ofSeconds(1));

        mockMvc.perform(get("/api/zones/{zoneId}/seats", fixture.zone().getId()).with(entryPassTokens.viewer(fixture.showtime().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status").value(contains("HELD", "HELD")));
    }

    private void lookUpSeats(HoldFixture fixture) throws Exception {
        mockMvc.perform(get("/api/zones/{zoneId}/seats", fixture.zone().getId()).with(entryPassTokens.viewer(fixture.showtime().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status").value(contains("AVAILABLE", "AVAILABLE")));
    }

    private void hold(HoldFixture fixture) throws Exception {
        mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(fixture.member().getId()))
                        .header(ENTRY_TOKEN, entryPassTokens.issue(fixture.member().getId(), fixture.showtime().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(fixture.seatIds())))
                .andExpect(status().isCreated());
    }
}
