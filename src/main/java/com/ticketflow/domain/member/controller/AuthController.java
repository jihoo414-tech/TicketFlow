package com.ticketflow.domain.member.controller;

import com.ticketflow.domain.member.dto.LoginRequest;
import com.ticketflow.domain.member.dto.LoginResponse;
import com.ticketflow.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse loginResponse = memberService.login(request.email(), request.password());

        return ResponseEntity.ok(loginResponse);
    }
}
