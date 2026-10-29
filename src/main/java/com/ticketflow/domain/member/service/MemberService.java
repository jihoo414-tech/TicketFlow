package com.ticketflow.domain.member.service;

import com.ticketflow.domain.member.dto.MemberResponse;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
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

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long memberId) {
        return MemberResponse.from(findMember(memberId));
    }

    @Transactional(readOnly = true)
    public void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public Member findLockedMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
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
