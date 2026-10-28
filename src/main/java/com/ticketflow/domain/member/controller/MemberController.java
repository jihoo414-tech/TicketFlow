package com.ticketflow.domain.member.controller;

import com.ticketflow.domain.member.dto.MemberResponse;
import com.ticketflow.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/api/members")
    public ResponseEntity<List<MemberResponse>> getMembers() {
        List<MemberResponse> memberResponseList = memberService.getMembers();

        return ResponseEntity.ok(memberResponseList);
    }

    @GetMapping("/api/members/me")
    public ResponseEntity<MemberResponse> getMe(@CurrentMemberId Long memberId) {
        MemberResponse memberResponse = memberService.getMember(memberId);

        return ResponseEntity.ok(memberResponse);
    }
}
