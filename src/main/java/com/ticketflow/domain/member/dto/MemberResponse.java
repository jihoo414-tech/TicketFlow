package com.ticketflow.domain.member.dto;

import com.ticketflow.domain.member.entity.Member;

public record MemberResponse(
        Long id,
        String name
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getName()
        );
    }
}
