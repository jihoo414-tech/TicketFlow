package com.ticketflow.domain.member.service;

import com.ticketflow.domain.member.dto.LoginResponse;
import com.ticketflow.domain.member.dto.MemberResponse;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;

    @Transactional
    public MemberResponse signup(String email, String password, String name) {
        validateEmailNotTaken(email);
        return MemberResponse.from(saveMember(new Member(email, passwordEncoder.encode(password), name)));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        Member member = findMemberByEmail(email);
        validatePasswordMatches(member, password);
        return new LoginResponse(accessTokenIssuer.issue(member.getId()));
    }

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long memberId) {
        return MemberResponse.from(findMember(memberId));
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

    private Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email == null ? "" : email)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
    }

    private Member saveMember(Member member) {
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void validatePasswordMatches(Member member, String password) {
        if (password == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }

    private void validateEmailNotTaken(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }
}
