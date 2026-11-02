package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.admission.service.EntryPass;
import com.ticketflow.domain.admission.service.EntryPasses;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static com.ticketflow.domain.admission.service.EntryPasses.ENTRY_TOKEN;

@RestController
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;
    private final EntryPasses entryPasses;

    @PostMapping("/api/showtimes/{showtimeId}/holds")
    public ResponseEntity<HoldResponse.Created> hold(@AuthenticationPrincipal Long memberId,
                                                     @RequestHeader(value = ENTRY_TOKEN, required = false) String entryToken,
                                                     @PathVariable Long showtimeId,
                                                     @Valid @RequestBody HoldRequest.Create request) {
        EntryPass pass = entryPasses.verify(entryToken, memberId, showtimeId);
        HoldResponse.Created holdResponse = holdService.hold(memberId, showtimeId, request.seatIds());
        entryPasses.complete(pass);

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
