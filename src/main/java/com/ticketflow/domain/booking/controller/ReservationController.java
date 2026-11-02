package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.booking.dto.ReservationResponse;
import com.ticketflow.domain.booking.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/api/holds/{holdId}/payment")
    public ResponseEntity<ReservationResponse.Created> pay(@AuthenticationPrincipal Long memberId,
                                                           @PathVariable Long holdId) {
        ReservationResponse.Created reservationResponse = reservationService.pay(memberId, holdId);

        return ResponseEntity.status(HttpStatus.CREATED).body(reservationResponse);
    }

    @PostMapping("/api/reservations/{reservationId}/cancellation")
    public ResponseEntity<ReservationResponse.Cancelled> cancel(@AuthenticationPrincipal Long memberId,
                                                                @PathVariable Long reservationId) {
        ReservationResponse.Cancelled reservationCancellationResponse = reservationService.cancel(memberId, reservationId);

        return ResponseEntity.ok(reservationCancellationResponse);
    }

    @GetMapping("/api/members/me/reservations")
    public ResponseEntity<List<ReservationResponse.Mine>> getMyReservations(@AuthenticationPrincipal Long memberId) {
        List<ReservationResponse.Mine> myReservationResponseList = reservationService.getMyReservations(memberId);

        return ResponseEntity.ok(myReservationResponseList);
    }
}
