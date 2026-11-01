package com.ticketflow.fixture;

import com.ticketflow.domain.member.entity.Member;

import java.util.UUID;

public final class TestMembers {

    private static final String PASSWORD_HASH = "login-disabled";

    private TestMembers() {
    }

    public static Member member(String name) {
        return new Member(UUID.randomUUID() + "@ticketflow.test", PASSWORD_HASH, name);
    }
}
