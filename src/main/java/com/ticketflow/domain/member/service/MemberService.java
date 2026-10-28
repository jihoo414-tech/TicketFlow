package com.ticketflow.domain.member.service;

import com.ticketflow.domain.member.dto.MemberResponse;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers() {
        return toResponses(findAllMembers());
    }

    private List<Member> findAllMembers() {
        return memberRepository.findAll();
    }

    private List<MemberResponse> toResponses(List<Member> members) {
        return members.stream()
                .map(MemberResponse::from)
                .toList();
    }
}
