package com.ticketflow.domain.member.dto;

import com.ticketflow.domain.member.entity.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberResponse {

    public record Item(
            Long id,
            String email,
            String name
    ) {

        public static Item from(Member member) {
            return new Item(
                    member.getId(),
                    member.getEmail(),
                    member.getName()
            );
        }
    }

    public record Login(
            String accessToken
    ) {
    }
}
