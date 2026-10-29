package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.booking.dto.ReservationResponse;
import com.ticketflow.domain.booking.service.ReservationService;
import com.ticketflow.domain.member.controller.CurrentMemberId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/api/holds/{holdId}/payment")
    public ResponseEntity<ReservationResponse> pay(@CurrentMemberId Long memberId,
                                                   @PathVariable Long holdId) {
        ReservationResponse reservationResponse = reservationService.pay(memberId, holdId);

        return ResponseEntity.status(HttpStatus.CREATED).body(reservationResponse);
    }
}
