package com.ticketflow.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemberRequest {

    public record Signup(
            @NotNull(message = "INVALID_EMAIL")
            @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "INVALID_EMAIL")
            String email,

            @NotNull(message = "INVALID_PASSWORD")
            @Size(min = 8, max = 20, message = "INVALID_PASSWORD")
            @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "INVALID_PASSWORD")
            String password,

            @NotBlank(message = "INVALID_MEMBER_NAME")
            @Size(max = 50, message = "INVALID_MEMBER_NAME")
            String name
    ) {
    }

    public record Login(
            String email,
            String password
    ) {
    }
}
