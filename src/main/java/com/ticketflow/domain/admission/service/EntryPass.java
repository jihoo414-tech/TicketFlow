package com.ticketflow.domain.admission.service;

import org.springframework.security.oauth2.jwt.Jwt;

public record EntryPass(String id, Long memberId, Long showtimeId) {

    public static EntryPass from(Jwt jwt) {
        return new EntryPass(
                jwt.getId(),
                Long.valueOf(jwt.getSubject()),
                Long.valueOf(jwt.getClaimAsString("showtimeId"))
        );
    }
}
