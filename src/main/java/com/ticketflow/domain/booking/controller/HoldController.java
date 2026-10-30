package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.booking.dto.HoldCancellationResponse;
import com.ticketflow.domain.booking.dto.HoldRequest;
import com.ticketflow.domain.booking.dto.HoldResponse;
import com.ticketflow.domain.booking.service.HoldService;
import com.ticketflow.domain.member.controller.CurrentMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    @PostMapping("/api/showtimes/{showtimeId}/holds")
    public ResponseEntity<HoldResponse> hold(@CurrentMemberId Long memberId,
                                             @PathVariable Long showtimeId,
                                             @Valid @RequestBody HoldRequest request) {
        HoldResponse holdResponse = holdService.hold(memberId, showtimeId, request.seatIds());

        return ResponseEntity.status(HttpStatus.CREATED).body(holdResponse);
    }

    @PostMapping("/api/holds/{holdId}/cancellation")
    public ResponseEntity<HoldCancellationResponse> cancel(@CurrentMemberId Long memberId,
                                                           @PathVariable Long holdId) {
        HoldCancellationResponse holdCancellationResponse = holdService.cancel(memberId, holdId);

        return ResponseEntity.ok(holdCancellationResponse);
    }
}
