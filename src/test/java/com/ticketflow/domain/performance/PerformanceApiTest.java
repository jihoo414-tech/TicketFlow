package com.ticketflow.domain.performance;

import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.performance.entity.Performance;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
