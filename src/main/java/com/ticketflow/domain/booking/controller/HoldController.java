package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.booking.dto.HoldRequest;
import com.ticketflow.domain.booking.dto.HoldResponse;
import com.ticketflow.domain.booking.dto.SeatResponse;
import com.ticketflow.domain.booking.service.HoldService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    @PostMapping("/api/showtimes/{showtimeId}/holds")
    public ResponseEntity<HoldResponse.Created> hold(@AuthenticationPrincipal Long memberId,
                                                     @PathVariable Long showtimeId,
                                                     @Valid @RequestBody HoldRequest.Create request) {
        HoldResponse.Created holdResponse = holdService.hold(memberId, showtimeId, request.seatIds());

        return ResponseEntity.status(HttpStatus.CREATED).body(holdResponse);
    }

    @PostMapping("/api/holds/{holdId}/cancellation")
    public ResponseEntity<HoldResponse.Cancelled> cancel(@AuthenticationPrincipal Long memberId,
                                                         @PathVariable Long holdId) {
        HoldResponse.Cancelled holdCancellationResponse = holdService.cancel(memberId, holdId);

        return ResponseEntity.ok(holdCancellationResponse);
    }

    @GetMapping("/api/members/me/showtimes/{showtimeId}/remaining-seat-count")
    public ResponseEntity<SeatResponse.RemainingCount> getRemainingSeatCount(@AuthenticationPrincipal Long memberId,
                                                                             @PathVariable Long showtimeId) {
        SeatResponse.RemainingCount remainingSeatCountResponse = holdService.getRemainingSeatCount(memberId, showtimeId);

        return ResponseEntity.ok(remainingSeatCountResponse);
    }
}
