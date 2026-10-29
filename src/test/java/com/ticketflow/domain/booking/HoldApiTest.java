package com.ticketflow.domain.booking;

import com.ticketflow.TestClock;
import com.ticketflow.TestcontainersConfiguration;
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

import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
}
