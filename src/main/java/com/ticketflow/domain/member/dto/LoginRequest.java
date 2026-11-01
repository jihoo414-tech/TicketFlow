package com.ticketflow.domain.member.dto;

public record LoginRequest(
        String email,
        String password
) {
}
