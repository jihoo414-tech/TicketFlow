package com.ticketflow.domain.member;

import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    MemberRepository memberRepository;

    @Test
    void 회원_목록을_조회하면_등록된_회원의_id와_이름이_모두_보인다() throws Exception {
        Member first = memberRepository.save(new Member("회원1"));
        Member second = memberRepository.save(new Member("회원2"));

        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(first.getId().intValue(), second.getId().intValue())))
                .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("회원1", "회원2")));
    }

    @Test
    void 회원_id_헤더를_실어_요청하면_그_회원의_id와_이름이_보인다() throws Exception {
        Member member = memberRepository.save(new Member("회원1"));

        mockMvc.perform(get("/api/members/me")
                        .header("X-Member-Id", member.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.name").value("회원1"));
    }

    @Test
    void 회원_id_헤더_없이_요청하면_회원_id가_없다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_ID_MISSING"));
    }

    @Test
    void 회원_id_헤더가_숫자가_아니면_회원_id가_올바르지_않다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/members/me")
                        .header("X-Member-Id", "abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_ID_INVALID"));
    }

    @Test
    void 없는_회원의_id로_요청하면_회원이_없다는_오류가_온다() throws Exception {
        mockMvc.perform(get("/api/members/me")
                        .header("X-Member-Id", Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }
}
