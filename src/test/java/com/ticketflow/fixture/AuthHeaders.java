package com.ticketflow.fixture;

import com.ticketflow.domain.member.service.AccessTokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthHeaders {

    private final AccessTokenIssuer accessTokenIssuer;

    public String bearer(Long memberId) {
        return "Bearer " + accessTokenIssuer.issue(memberId);
    }
}
