package com.ticketflow.domain.performance;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.entity.Seat;
import com.ticketflow.domain.performance.entity.Showtime;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PerformanceApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PerformanceRepository performanceRepository;

    @Autowired
    ShowtimeRepository showtimeRepository;

    @Autowired
    SeatRepository seatRepository;

    @Test
    void 공연_목록을_조회하면_등록된_공연이_모두_보인다() throws Exception {
        performanceRepository.saveAll(List.of(
                new Performance("레미제라블"),
                new Performance("오페라의 유령")
        ));

        mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(containsInAnyOrder("레미제라블", "오페라의 유령")));
    }

    @Test
    void 공연의_회차_목록을_조회하면_그_공연의_회차만_시작_시각_순으로_보인다() throws Exception {
        Performance lesMis = performanceRepository.save(new Performance("레미제라블"));
        Performance phantom = performanceRepository.save(new Performance("오페라의 유령"));
        LocalDateTime openAt = LocalDateTime.of(2026, 10, 1, 10, 0, 0);
        showtimeRepository.saveAll(List.of(
                Showtime.create(lesMis, LocalDateTime.of(2026, 11, 1, 19, 0, 0), openAt, 50000),
                Showtime.create(lesMis, LocalDateTime.of(2026, 11, 1, 14, 0, 0), openAt, 50000),
                Showtime.create(phantom, LocalDateTime.of(2026, 11, 2, 19, 0, 0), openAt, 60000)
        ));

        mockMvc.perform(get("/api/performances/{performanceId}/showtimes", lesMis.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].startsAt").value(contains("2026-11-01T14:00:00", "2026-11-01T19:00:00")));
    }

    @Test
    void 없는_공연의_회차_목록을_조회하면_공연이_없다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/performances/{performanceId}/showtimes", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
    }

    @Test
    void 회차가_없는_공연의_회차_목록을_조회하면_빈_목록이_보인다() throws Exception {
        Performance lesMis = performanceRepository.save(new Performance("레미제라블"));

        mockMvc.perform(get("/api/performances/{performanceId}/showtimes", lesMis.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void 회차의_좌석_목록을_조회하면_각_좌석의_열_번호_가격_상태가_보인다() throws Exception {
        Performance lesMis = performanceRepository.save(new Performance("레미제라블"));
        Showtime showtime = showtimeRepository.save(Showtime.create(
                lesMis, LocalDateTime.of(2026, 11, 1, 19, 0, 0), LocalDateTime.of(2026, 10, 1, 10, 0, 0), 50000));
        seatRepository.saveAll(List.of(
                new Seat(showtime, "A", 1),
                new Seat(showtime, "A", 2),
                new Seat(showtime, "B", 1)
        ));

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", showtime.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].row").value(contains("A", "A", "B")))
                .andExpect(jsonPath("$[*].number").value(contains(1, 2, 1)))
                .andExpect(jsonPath("$[*].price").value(contains(50000, 50000, 50000)))
                .andExpect(jsonPath("$[*].status").value(contains("AVAILABLE", "AVAILABLE", "AVAILABLE")));
    }

    @Test
    void 없는_회차의_좌석_목록을_조회하면_회차가_없다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOWTIME_NOT_FOUND"));
    }

    @Test
    void 두_회차의_같은_위치는_서로_다른_좌석으로_조회된다() throws Exception {
        Performance lesMis = performanceRepository.save(new Performance("레미제라블"));
        LocalDateTime openAt = LocalDateTime.of(2026, 10, 1, 10, 0, 0);
        Showtime evening = showtimeRepository.save(Showtime.create(
                lesMis, LocalDateTime.of(2026, 11, 1, 19, 0, 0), openAt, 50000));
        Showtime matinee = showtimeRepository.save(Showtime.create(
                lesMis, LocalDateTime.of(2026, 11, 2, 14, 0, 0), openAt, 50000));
        seatRepository.saveAll(List.of(
                new Seat(evening, "A", 1),
                new Seat(matinee, "A", 1)
        ));

        Long eveningSeatId = findOnlySeatId(evening);
        Long matineeSeatId = findOnlySeatId(matinee);

        assertThat(eveningSeatId).isNotEqualTo(matineeSeatId);
    }

    private Long findOnlySeatId(Showtime showtime) throws Exception {
        String body = mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", showtime.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].row").value(contains("A")))
                .andExpect(jsonPath("$[*].number").value(contains(1)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$[0].id", Long.class);
    }
}
