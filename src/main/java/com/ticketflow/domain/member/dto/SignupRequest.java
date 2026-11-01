package com.ticketflow.domain.member.dto;

public record SignupRequest(
        String email,
        String password,
        String name
) {
}
