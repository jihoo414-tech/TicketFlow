package com.ticketflow.domain.member.controller;

import com.ticketflow.domain.member.dto.MemberRequest;
import com.ticketflow.domain.member.dto.MemberResponse;
import com.ticketflow.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/api/members")
    public ResponseEntity<MemberResponse.Item> signup(@Valid @RequestBody MemberRequest.Signup request) {
        MemberResponse.Item memberResponse = memberService.signup(request.email(), request.password(), request.name());

        return ResponseEntity.status(HttpStatus.CREATED).body(memberResponse);
    }

    @GetMapping("/api/members/me")
    public ResponseEntity<MemberResponse.Item> getMe(@AuthenticationPrincipal Long memberId) {
        MemberResponse.Item memberResponse = memberService.getMember(memberId);

        return ResponseEntity.ok(memberResponse);
    }
}
